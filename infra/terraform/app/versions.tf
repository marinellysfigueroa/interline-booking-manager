# Stack "app": la aplicación desplegada. Lo aplica CI (gh-terraform) desde la rama principal.
terraform {
  required_version = ">= 1.11" # argumentos write-only y recursos efímeros

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.6"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  # Backend parcial: bucket y prefijo se pasan en `terraform init -backend-config=...`.
  backend "gcs" {}
}

provider "google" {
  project = var.project_id
  region  = var.region

  default_labels = {
    app         = "interline-booking-manager"
    environment = var.environment
    managed-by  = "terraform"
  }
}
