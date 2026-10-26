variable "aws_region" {
  description = "AWS region for provisioning resources"
  type        = string
  default     = "us-east-1"
}

variable "localstack_endpoint" {
  description = "LocalStack endpoint URL for local emulation"
  type        = string
  default     = "http://localhost:4566"
}

variable "environment" {
  description = "Deployment environment name"
  type        = string
  default     = "local"
}
