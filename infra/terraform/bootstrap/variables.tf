variable "project_id" {
  description = "Proyecto de GCP donde vive la aplicación."
  type        = string
}

variable "region" {
  description = "Región por defecto (Cloud Run, Cloud SQL, Artifact Registry)."
  type        = string
  default     = "us-central1"
}

variable "github_repository" {
  description = "Repositorio de GitHub (owner/nombre) autorizado a usar Workload Identity Federation."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Formato esperado: owner/repo."
  }
}

variable "deploy_branch" {
  description = "Única rama desde la que CI puede desplegar y aplicar Terraform."
  type        = string
  default     = "main"
}

variable "state_bucket_name" {
  description = "Nombre global del bucket del state de Terraform (vacío = <project>-tfstate)."
  type        = string
  default     = ""
}
