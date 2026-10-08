# ---------------------------------------------------------------- identidades de runtime
# Una cuenta por servicio, con el mínimo imprescindible (nunca la cuenta por defecto de Compute).
resource "google_service_account" "backend" {
  account_id   = "${local.prefix}-backend"
  display_name = "Cloud Run: backend Quarkus"
}

resource "google_service_account" "frontend" {
  account_id   = "${local.prefix}-frontend"
  display_name = "Cloud Run: frontend nginx"
}

resource "google_service_account" "provider_sandbox" {
  count        = var.deploy_provider_sandbox ? 1 : 0
  account_id   = "${local.prefix}-sandbox"
  display_name = "Cloud Run: sandbox WireMock del proveedor de vuelos"
}

resource "google_project_iam_member" "backend" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/cloudtrace.agent",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.backend.email}"
}

resource "google_project_iam_member" "frontend_logs" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.frontend.email}"
}

# El backend solo puede leer SUS secretos (permiso a nivel de secreto, no de proyecto).
resource "google_secret_manager_secret_iam_member" "backend_db_password" {
  secret_id = google_secret_manager_secret.db_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.backend.email}"
}

data "google_secret_manager_secret" "external" {
  for_each  = toset(["amadeus-client-id", "amadeus-client-secret"])
  secret_id = each.value
}

resource "google_secret_manager_secret_iam_member" "backend_external" {
  for_each  = data.google_secret_manager_secret.external
  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.backend.email}"
}

# gh-deployer despliega revisiones que corren con estas identidades ("actAs").
resource "google_service_account_iam_member" "deployer_act_as" {
  for_each = merge(
    {
      backend  = google_service_account.backend.name
      frontend = google_service_account.frontend.name
    },
    var.deploy_provider_sandbox ? { sandbox = google_service_account.provider_sandbox[0].name } : {}
  )
  service_account_id = each.value
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${var.deployer_service_account}"
}
