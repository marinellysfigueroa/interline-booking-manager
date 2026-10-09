# terraform test (sin credenciales de GCP): el provider google se simula y se comprueban las
# decisiones de seguridad del stack sobre el plan y el estado resultantes.
mock_provider "google" {
  mock_resource "google_service_account" {
    defaults = {
      name  = "projects/test-project/serviceAccounts/mock-sa@test-project.iam.gserviceaccount.com"
      email = "mock-sa@test-project.iam.gserviceaccount.com"
    }
  }
  mock_data "google_secret_manager_secret" {
    defaults = {
      id = "projects/p/secrets/mock"
    }
  }
  mock_resource "google_compute_network" {
    defaults = {
      id = "projects/p/global/networks/interline-prod-vpc"
    }
  }
  mock_resource "google_compute_subnetwork" {
    defaults = {
      id = "projects/p/regions/us-central1/subnetworks/interline-prod-run"
    }
  }
  mock_resource "google_cloud_run_v2_service" {
    defaults = {
      uri = "https://mock-service.a.run.app"
    }
  }
  mock_resource "google_sql_database_instance" {
    defaults = {
      private_ip_address = "10.20.0.3"
    }
  }
}

variables {
  project_id               = "test-project"
  deployer_service_account = "gh-deployer@test-project.iam.gserviceaccount.com"
}

run "no_secrets_in_state" {
  command = apply

  assert {
    condition     = google_secret_manager_secret_version.db_password.secret_data == null
    error_message = "La contraseña no debe guardarse en el state (usar secret_data_wo)."
  }
  assert {
    condition     = google_sql_user.app.password == null
    error_message = "La contraseña de Cloud SQL no debe guardarse en el state (usar password_wo)."
  }
  assert {
    condition     = tostring(google_sql_user.app.password_wo_version) == tostring(google_secret_manager_secret_version.db_password.secret_data_wo_version)
    error_message = "Secret Manager y Cloud SQL deben rotar la contraseña a la vez."
  }
}

run "database_is_private" {
  command = plan

  assert {
    condition     = google_sql_database_instance.main.settings[0].ip_configuration[0].ipv4_enabled == false
    error_message = "Cloud SQL no debe tener IP pública."
  }
  assert {
    condition     = google_sql_database_instance.main.settings[0].ip_configuration[0].ssl_mode == "ENCRYPTED_ONLY"
    error_message = "Las conexiones a Cloud SQL deben ir cifradas."
  }
  assert {
    condition     = google_sql_database_instance.main.settings[0].backup_configuration[0].point_in_time_recovery_enabled
    error_message = "Se espera point-in-time recovery."
  }
}

run "backend_is_not_reachable_from_internet" {
  command = plan

  assert {
    condition     = google_cloud_run_v2_service.backend.ingress == "INGRESS_TRAFFIC_INTERNAL_ONLY"
    error_message = "El backend solo debe aceptar tráfico interno."
  }
  assert {
    condition     = google_cloud_run_v2_service.frontend.template[0].vpc_access[0].egress == "ALL_TRAFFIC"
    error_message = "El frontend debe salir por la VPC para que sus llamadas al backend sean internas."
  }
  assert {
    condition     = google_compute_subnetwork.run.private_ip_google_access
    error_message = "La subred necesita Private Google Access para llegar al backend interno."
  }
}

run "backend_reads_secrets_from_secret_manager" {
  command = plan

  assert {
    condition = alltrue([
      for e in google_cloud_run_v2_service.backend.template[0].containers[0].env :
      e.value == null || e.value == "" || !can(regex("(?i)password|secret", e.name))
    ])
    error_message = "Ninguna variable con contraseña o secreto puede tener valor en claro."
  }
  assert {
    condition     = google_cloud_run_v2_service.backend.template[0].service_account != ""
    error_message = "El backend debe correr con su propia cuenta de servicio."
  }
}

run "sandbox_is_optional" {
  command = plan

  variables {
    deploy_provider_sandbox = false
    amadeus_base_url        = "https://api.real-provider.example"
  }

  assert {
    condition     = length(google_cloud_run_v2_service.provider_sandbox) == 0
    error_message = "Sin sandbox no debe crearse el servicio."
  }
}
