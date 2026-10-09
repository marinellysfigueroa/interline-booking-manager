variable "project_id" {
  description = "Proyecto de GCP."
  type        = string
}

variable "region" {
  description = "Región de Cloud Run, Cloud SQL y Artifact Registry."
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Nombre corto del entorno; prefijo de los recursos."
  type        = string
  default     = "prod"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,10}$", var.environment))
    error_message = "Minúsculas, números y guiones; 2 a 11 caracteres."
  }
}

variable "deployer_service_account" {
  description = "Email de la cuenta gh-deployer creada por el stack bootstrap."
  type        = string
}

# ---------------------------------------------------------------- red
variable "subnet_cidr" {
  description = "Rango de la subred usada por Direct VPC egress de Cloud Run."
  type        = string
  default     = "10.10.0.0/24"
}

# ---------------------------------------------------------------- base de datos
variable "db_tier" {
  description = "Tipo de máquina de Cloud SQL (db-f1-micro para demo; db-custom-* en producción)."
  type        = string
  default     = "db-f1-micro"
}

variable "db_availability_type" {
  description = "ZONAL o REGIONAL (alta disponibilidad)."
  type        = string
  default     = "ZONAL"
}

variable "db_deletion_protection" {
  description = "Protege la instancia de Cloud SQL contra un destroy accidental."
  type        = bool
  default     = true
}

variable "db_password_version" {
  description = <<-EOT
    Versión de la contraseña de la base de datos. La contraseña se genera como valor efímero y
    se escribe con argumentos write-only: nunca queda en el state. Incrementar este número la
    rota (Secret Manager y Cloud SQL a la vez); después hay que desplegar una revisión nueva del
    backend para que lea la versión nueva del secreto.
  EOT
  type        = number
  default     = 1
}

# ---------------------------------------------------------------- Cloud Run
variable "placeholder_image" {
  description = "Imagen inicial de los servicios; CI la sustituye en cada despliegue (Terraform ignora el cambio)."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}

variable "backend_min_instances" {
  description = "Instancias mínimas del backend (0 = escala a cero; 1 evita arranques en frío)."
  type        = number
  default     = 0
}

variable "backend_max_instances" {
  type    = number
  default = 4
}

variable "frontend_max_instances" {
  type    = number
  default = 4
}

variable "deletion_protection" {
  description = "Protege los servicios de Cloud Run contra un destroy accidental."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------- proveedor de ofertas
variable "flight_offers_provider" {
  description = "Adaptador de ofertas del backend: amadeus-style o simulated."
  type        = string
  default     = "amadeus-style"

  validation {
    condition     = contains(["amadeus-style", "simulated"], var.flight_offers_provider)
    error_message = "Valores válidos: amadeus-style, simulated."
  }
}

variable "deploy_provider_sandbox" {
  description = "Despliega el sandbox WireMock (contrato estilo Amadeus con datos ficticios) como proveedor."
  type        = bool
  default     = true
}

variable "amadeus_base_url" {
  description = "URL de un proveedor real; si se deja vacía y hay sandbox, se usa la URL del sandbox."
  type        = string
  default     = ""
}

# ---------------------------------------------------------------- seguridad
variable "oidc_enabled" {
  description = "Exige un token OIDC en /api/* (requiere un IdP configurado)."
  type        = bool
  default     = false
}

variable "oidc_auth_server_url" {
  description = "Issuer OIDC (p. ej. https://accounts.google.com o un realm de Keycloak)."
  type        = string
  default     = ""
}

variable "oidc_client_id" {
  type    = string
  default = "interline-api"
}
