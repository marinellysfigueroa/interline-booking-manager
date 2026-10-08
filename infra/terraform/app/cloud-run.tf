# =============================================================================
# Cloud Run
#
#   Internet ──► frontend (nginx, público) ──VPC──► backend (ingress interno) ──VPC──► Cloud SQL
#                                                        └──internet──► sandbox WireMock / proveedor
#
# Las imágenes las despliega CI (`gcloud run services update --image`); Terraform crea los
# servicios con una imagen inicial e ignora después ese campo para no revertir despliegues.
# =============================================================================

locals {
  amadeus_base_url = var.amadeus_base_url != "" ? var.amadeus_base_url : (
    var.deploy_provider_sandbox ? google_cloud_run_v2_service.provider_sandbox[0].uri : ""
  )
}

# ---------------------------------------------------------------- backend
resource "google_cloud_run_v2_service" "backend" {
  name                = "${local.prefix}-backend"
  location            = var.region
  deletion_protection = var.deletion_protection

  # Solo tráfico interno: desde internet no se llega al backend, solo a través del frontend.
  ingress = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  # El ingress interno ya restringe el origen; sin IAM para que nginx no tenga que firmar tokens.
  invoker_iam_disabled = true

  template {
    service_account                  = google_service_account.backend.email
    max_instance_request_concurrency = 80
    timeout                          = "60s"

    scaling {
      min_instance_count = var.backend_min_instances
      max_instance_count = var.backend_max_instances
    }

    vpc_access {
      # Solo los rangos privados (Cloud SQL) van por la VPC; el resto sale directo a internet.
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.run.id
      }
    }

    containers {
      image = var.placeholder_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true # CPU solo durante las peticiones (escala a cero barata)
        startup_cpu_boost = true # arranque JVM más rápido
      }

      env {
        name  = "DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.main.private_ip_address}:5432/${google_sql_database.app.name}?sslmode=require"
      }
      env {
        name  = "DB_USER"
        value = google_sql_user.app.name
      }
      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "FLIGHT_OFFERS_PROVIDER"
        value = var.flight_offers_provider
      }
      env {
        name  = "AMADEUS_BASE_URL"
        value = local.amadeus_base_url
      }
      env {
        name = "AMADEUS_CLIENT_ID"
        value_source {
          secret_key_ref {
            secret  = data.google_secret_manager_secret.external["amadeus-client-id"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "AMADEUS_CLIENT_SECRET"
        value_source {
          secret_key_ref {
            secret  = data.google_secret_manager_secret.external["amadeus-client-secret"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "OIDC_ENABLED"
        value = tostring(var.oidc_enabled)
      }
      env {
        name  = "OIDC_AUTH_SERVER_URL"
        value = var.oidc_auth_server_url
      }
      env {
        name  = "OIDC_CLIENT_ID"
        value = var.oidc_client_id
      }
      env {
        # Sin colector OTLP en este entorno: trazas desactivadas (logs JSON → Cloud Logging).
        name  = "QUARKUS_OTEL_SDK_DISABLED"
        value = "true"
      }
      env {
        # Formato estructurado de Cloud Logging (severity, trace...): logs filtrables por nivel.
        name  = "QUARKUS_LOG_CONSOLE_JSON_LOG_FORMAT"
        value = "gcp"
      }

      startup_probe {
        http_get {
          path = "/q/health/started"
        }
        initial_delay_seconds = 2
        period_seconds        = 3
        failure_threshold     = 20
        timeout_seconds       = 2
      }

      liveness_probe {
        http_get {
          path = "/q/health/live"
        }
        period_seconds  = 15
        timeout_seconds = 3
      }
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].containers[0].image, # lo gestiona CI
      client,
      client_version,
    ]
  }

  depends_on = [
    google_secret_manager_secret_version.db_password,
    google_secret_manager_secret_iam_member.backend_db_password,
    google_secret_manager_secret_iam_member.backend_external,
  ]
}

# ---------------------------------------------------------------- frontend
resource "google_cloud_run_v2_service" "frontend" {
  name                 = "${local.prefix}-frontend"
  location             = var.region
  deletion_protection  = var.deletion_protection
  ingress              = "INGRESS_TRAFFIC_ALL"
  invoker_iam_disabled = true # sitio público

  template {
    service_account                  = google_service_account.frontend.email
    max_instance_request_concurrency = 200

    scaling {
      min_instance_count = 0
      max_instance_count = var.frontend_max_instances
    }

    vpc_access {
      # Todo el tráfico por la VPC: así sus llamadas al backend cuentan como internas.
      egress = "ALL_TRAFFIC"
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.run.id
      }
    }

    containers {
      image = var.placeholder_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "256Mi"
        }
        cpu_idle = true
      }

      env {
        name  = "API_UPSTREAM"
        value = google_cloud_run_v2_service.backend.uri
      }

      startup_probe {
        http_get {
          path = "/healthz"
        }
        period_seconds    = 2
        failure_threshold = 10
      }

      liveness_probe {
        http_get {
          path = "/healthz"
        }
        period_seconds = 30
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image, client, client_version]
  }
}

# ---------------------------------------------------------------- sandbox del proveedor (opcional)
# WireMock con el contrato estilo Amadeus y datos FICTICIOS: no expone nada sensible, por eso es
# público. Con un proveedor real, deploy_provider_sandbox = false y amadeus_base_url = su URL.
resource "google_cloud_run_v2_service" "provider_sandbox" {
  count                = var.deploy_provider_sandbox ? 1 : 0
  name                 = "${local.prefix}-provider-sandbox"
  location             = var.region
  deletion_protection  = var.deletion_protection
  ingress              = "INGRESS_TRAFFIC_ALL"
  invoker_iam_disabled = true

  template {
    service_account = google_service_account.provider_sandbox[0].email

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = var.placeholder_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image, client, client_version]
  }
}
