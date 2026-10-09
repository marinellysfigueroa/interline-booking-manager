# Stack "bootstrap": se aplica UNA vez, a mano, con una identidad con permisos de Owner.
# Crea lo que CI necesita para autenticarse y guardar el state; no contiene secretos, así que
# su propio state puede quedarse local o migrarse al bucket (ver README de infra).
terraform {
  required_version = ">= 1.11" # argumentos write-only y recursos efímeros

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.6"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
