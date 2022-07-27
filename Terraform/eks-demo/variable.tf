variable "region" {
  default     = "us-east-1"
  description = "AWS region"
}

variable "oidc_thumbprint_list" {
  type    = list(any)
  default = []
}

variable "namespace" {
  default     = "prometheus"
}