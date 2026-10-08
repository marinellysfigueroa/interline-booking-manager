locals {
  state_bucket = var.state_bucket_name != "" ? var.state_bucket_name : "${var.project_id}-tfstate"
  # Atributo compuesto "owner/repo@refs/heads/main": permite dar permisos solo a esa rama.
  main_ref = "${var.github_repository}@refs/heads/${var.deploy_branch}"
  pool     = google_iam_workload_identity_pool.github.name

  apis = [
    "artifactregistry.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "compute.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "servicenetworking.googleapis.com",
    "sqladmin.googleapis.com",
    "sts.googleapis.com",
  ]
}

resource "google_project_service" "apis" {
  for_each           = toset(local.apis)
  service            = each.value
  disable_on_destroy = false
}

# ---------------------------------------------------------------- state de Terraform
resource "google_storage_bucket" "tfstate" {
  name                        = local.state_bucket
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false

  versioning {
    enabled = true # permite recuperar un state corrupto o borrado
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 20
    }
    action {
      type = "Delete"
    }
  }
}

# ---------------------------------------------------------------- Workload Identity Federation
# GitHub Actions obtiene un token OIDC efímero y lo cambia por credenciales de GCP de corta
# duración: no existe ninguna llave JSON de cuenta de servicio.
resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"
  depends_on                = [google_project_service.apis]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-oidc"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"           = "assertion.sub"
    "attribute.repository"     = "assertion.repository"
    "attribute.repository_ref" = "assertion.repository + '@' + assertion.ref"
    "attribute.event_name"     = "assertion.event_name"
    "attribute.repository_id"  = "assertion.repository_id"
  }

  # Ningún token de otro repositorio puede siquiera usar el proveedor.
  attribute_condition = "assertion.repository == '${var.github_repository}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# ---------------------------------------------------------------- cuentas de servicio de CI
resource "google_service_account" "deployer" {
  account_id   = "gh-deployer"
  display_name = "GitHub Actions: publicar imágenes y desplegar Cloud Run (rama ${var.deploy_branch})"
}

resource "google_service_account" "terraform" {
  account_id   = "gh-terraform"
  display_name = "GitHub Actions: terraform apply del stack app (rama ${var.deploy_branch})"
}

resource "google_service_account" "terraform_plan" {
  account_id   = "gh-terraform-plan"
  display_name = "GitHub Actions: terraform plan (solo lectura, pull requests)"
}

# Quién puede hacerse pasar por cada cuenta (impersonación vía WIF)
resource "google_service_account_iam_member" "deployer_wif" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${local.pool}/attribute.repository_ref/${local.main_ref}"
}

resource "google_service_account_iam_member" "terraform_wif" {
  service_account_id = google_service_account.terraform.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${local.pool}/attribute.repository_ref/${local.main_ref}"
}

resource "google_service_account_iam_member" "terraform_plan_wif" {
  service_account_id = google_service_account.terraform_plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${local.pool}/attribute.repository/${var.github_repository}"
}

# ---------------------------------------------------------------- permisos
# gh-terraform administra los recursos del stack app. Roles acotados a los servicios que usa
# (no Owner/Editor).
resource "google_project_iam_member" "terraform" {
  for_each = toset([
    "roles/artifactregistry.admin",
    "roles/cloudsql.admin",
    "roles/compute.networkAdmin",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.serviceAccountUser",
    "roles/resourcemanager.projectIamAdmin",
    "roles/run.admin",
    "roles/secretmanager.admin",
    "roles/servicenetworking.networksAdmin",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.terraform.email}"
}

# gh-terraform-plan solo lee (el plan necesita refrescar el estado real de los recursos).
resource "google_project_iam_member" "terraform_plan" {
  for_each = toset([
    "roles/viewer",
    "roles/iam.securityReviewer",
    "roles/secretmanager.viewer",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.terraform_plan.email}"
}

# Acceso al state: apply escribe; plan necesita escribir el lock del backend GCS.
# Claves estáticas: los emails solo se conocen tras el apply y no pueden ser claves de for_each.
resource "google_storage_bucket_iam_member" "state_terraform" {
  for_each = {
    terraform      = google_service_account.terraform.email
    terraform_plan = google_service_account.terraform_plan.email
  }
  bucket = google_storage_bucket.tfstate.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${each.value}"
}

# gh-deployer solo despliega revisiones nuevas en servicios existentes; los permisos sobre el
# repositorio de Artifact Registry y sobre las cuentas de runtime se dan en el stack app.
resource "google_project_iam_member" "deployer_run" {
  project = var.project_id
  role    = "roles/run.developer"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

# ---------------------------------------------------------------- secretos gestionados fuera de Terraform
# Solo el "contenedor" del secreto: el valor se carga una vez con gcloud (ver README), así
# nunca pasa por Terraform ni por su state.
resource "google_secret_manager_secret" "external" {
  for_each  = toset(["amadeus-client-id", "amadeus-client-secret"])
  secret_id = each.value

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}
