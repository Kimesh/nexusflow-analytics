resource "aws_s3_bucket" "audit_archive" {
  bucket        = "nexusflow-audit-archive"
  force_destroy = true

  tags = {
    Environment = var.environment
    Project     = "NexusFlow-Analytics"
    Compliance  = "SOC2-Audit-Vault"
  }
}

resource "aws_s3_bucket_versioning" "audit_archive_versioning" {
  bucket = aws_s3_bucket.audit_archive.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit_archive_encryption" {
  bucket = aws_s3_bucket.audit_archive.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "audit_archive_lifecycle" {
  bucket = aws_s3_bucket.audit_archive.id

  rule {
    id     = "archive-retention-rule"
    status = "Enabled"

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 365
    }
  }
}
