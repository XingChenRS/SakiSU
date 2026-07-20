use std::io::{Read, Seek, SeekFrom};

use anyhow::{Result, ensure};

pub fn get_apk_signature(apk: &str) -> Result<(u32, String)> {
    let mut buffer = [0u8; 0x10];
    let mut size4 = [0u8; 4];
    let mut size8 = [0u8; 8];
    let mut size_of_block = [0u8; 8];

    let mut f = std::fs::File::open(apk)?;

    let mut i = 0;
    loop {
        let mut n = [0u8; 2];
        f.seek(SeekFrom::End(-i - 2))?;
        f.read_exact(&mut n)?;

        let n = u16::from_le_bytes(n);
        if i64::from(n) == i {
            f.seek(SeekFrom::Current(-22))?;
            f.read_exact(&mut size4)?;

            if u32::from_le_bytes(size4) ^ 0xcafe_babe_u32 == 0xccfb_f1ee_u32 {
                if i > 0 {
                    println!("warning: comment length is {i}");
                }
                break;
            }
        }

        ensure!(n != 0xffff, "not a zip file");

        i += 1;
    }

    f.seek(SeekFrom::Current(12))?;
    // offset
    f.read_exact(&mut size4)?;
    f.seek(SeekFrom::Start(u64::from(u32::from_le_bytes(size4)) - 0x18))?;

    f.read_exact(&mut size8)?;
    f.read_exact(&mut buffer)?;

    ensure!(&buffer == b"APK Sig Block 42", "Can not found sig block");

    let pos = u64::from(u32::from_le_bytes(size4)) - (u64::from_le_bytes(size8) + 0x8);
    f.seek(SeekFrom::Start(pos))?;
    f.read_exact(&mut size_of_block)?;

    ensure!(size_of_block == size8, "not a signed apk");

    let mut v2_signing: Option<(u32, String)> = None;
    let mut v3_signing: Option<(u32, String)> = None;
    let mut v3_1_signing: Option<(u32, String)> = None;
    let mut v2_block_count = 0;

    loop {
        let mut id = [0u8; 4];
        let mut offset = 4u32;

        f.read_exact(&mut size8)?; // sequence length
        if size8 == size_of_block {
            break;
        }

        f.read_exact(&mut id)?; // id

        let id = u32::from_le_bytes(id);
        if id == 0x7109_871a_u32 {
            v2_block_count += 1;

            // CVE-2023-46139: only process the FIRST v2 block (Android behavior)
            // Reject APKs with multiple v2 blocks
            if v2_block_count > 1 {
                return Err(anyhow::anyhow!("Duplicate v2 signature block detected"));
            }

            v2_signing = Some(calc_cert_sha256(&mut f, &mut size4, &mut offset)?);
        } else if id == 0xf053_68c0_u32 {
            // v3 uses the same first-cert prefix that we need for diagnostics.
            v3_signing = Some(calc_cert_sha256(&mut f, &mut size4, &mut offset)?);
        } else if id == 0x1b93_ad61_u32 {
            // v3.1 rotation metadata appears after the first certificate.
            v3_1_signing = Some(calc_cert_sha256(&mut f, &mut size4, &mut offset)?);
        }

        f.seek(SeekFrom::Current(
            i64::from_le_bytes(size8) - i64::from(offset),
        ))?;
    }

    // CVE-2023-46139: Ensure exactly one v2 signature block
    if v2_block_count != 1 {
        return Err(anyhow::anyhow!("Expected exactly 1 v2 signature block, found {}", v2_block_count));
    }

    // Validate v2 signature is present
    let v2_sig = v2_signing.ok_or_else(|| anyhow::anyhow!("No v2 signature found"))?;

    // If v3 or v3.1 present, they must also be valid (cross-verify)
    // This prevents attackers from using different certs in different schemes
    if let Some(v3_sig) = v3_signing {
        if v3_sig != v2_sig {
            return Err(anyhow::anyhow!("v3 signature cert does not match v2 cert"));
        }
    }

    if let Some(v3_1_sig) = v3_1_signing {
        if v3_1_sig != v2_sig {
            return Err(anyhow::anyhow!("v3.1 signature cert does not match v2 cert"));
        }
    }

    Ok(v2_sig)
}

fn calc_cert_sha256(
    f: &mut std::fs::File,
    size4: &mut [u8; 4],
    offset: &mut u32,
) -> Result<(u32, String)> {
    f.read_exact(size4)?; // signer-sequence length
    f.read_exact(size4)?; // signer length
    f.read_exact(size4)?; // signed data length
    *offset += 0x4 * 3;

    f.read_exact(size4)?; // digests-sequence length
    let pos = u32::from_le_bytes(*size4); // skip digests
    f.seek(SeekFrom::Current(i64::from(pos)))?;
    *offset += 0x4 + pos;

    f.read_exact(size4)?; // certificates length
    f.read_exact(size4)?; // certificate length
    *offset += 0x4 * 2;

    let cert_len = u32::from_le_bytes(*size4);
    let mut cert: Vec<u8> = vec![0; cert_len as usize];
    f.read_exact(&mut cert)?;
    *offset += cert_len;

    Ok((cert_len, sha256::digest(&cert)))
}
