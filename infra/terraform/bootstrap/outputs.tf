# Valores para las *variables* (no secretos) del repositorio de GitHub y para el stack app.
output "github_variables" {
  description = "Configurar como Repository variables en GitHub (Settings → Secrets and variables → Actions → Variables)."
  value = {
    GCP_PROJECT_ID        = var.project_id
    GCP_REGION            = var.region
    GCP_WIF_PROVIDER      = google_iam_workload_identity_pool_provider.github.name
    GCP_DEPLOYER_SA       = google_service_account.deployer.email
    GCP_TERRAFORM_SA      = google_service_account.terraform.email
    GCP_TERRAFORM_PLAN_SA = google_service_account.terraform_plan.email
    GCP_TF_STATE_BUCKET   = google_storage_bucket.tfstate.name
  }
}

output "deployer_service_account" {
  value = google_service_account.deployer.email
}

output "external_secret_ids" {
  description = "Secretos a los que hay que añadir un valor con gcloud antes de aplicar el stack app."
  value       = [for s in google_secret_manager_secret.external : s.secret_id]
}
