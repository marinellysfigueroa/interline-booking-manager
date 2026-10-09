output "frontend_url" {
  description = "URL pública de la aplicación."
  value       = google_cloud_run_v2_service.frontend.uri
}

output "backend_service" {
  description = "Nombre del servicio backend (ingress interno; se accede vía frontend /api)."
  value       = google_cloud_run_v2_service.backend.name
}

output "frontend_service" {
  value = google_cloud_run_v2_service.frontend.name
}

output "provider_sandbox_service" {
  value = var.deploy_provider_sandbox ? google_cloud_run_v2_service.provider_sandbox[0].name : null
}

output "artifact_registry" {
  description = "Prefijo de las imágenes: <registry>/backend, /frontend, /provider-sandbox."
  value       = local.images
}

output "cloud_sql_instance" {
  value = google_sql_database_instance.main.connection_name
}
