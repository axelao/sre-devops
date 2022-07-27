terraform {
  required_providers {
    random = {
      source  = "hashicorp/random"
      version = "3.1.0"
    }

    local = {
      source  = "hashicorp/local"
      version = "2.1.0"
    }
   }

### bucket for backend terraform
  backend "s3" {
    bucket = "terraform-backend-state-demo" # Will be overridden from build
    key    = "path/to/my/key" # Will be overridden from build
    region = "us-east-1"
  }

  required_version =  ">= 0.14"
}