# ---------------------------------------------------------------- Cloud SQL (PostgreSQL 17)
resource "google_sql_database_instance" "main" {
  name                = "${local.prefix}-pg"
  database_version    = "POSTGRES_17"
  region              = var.region
  deletion_protection = var.db_deletion_protection

  settings {
    edition           = "ENTERPRISE"
    tier              = var.db_tier
    availability_type = var.db_availability_type
    disk_autoresize   = true

    # La regla AVD-GCP-0015 comprueba el atributo retirado `require_ssl`; su sustituto,
    # ssl_mode = ENCRYPTED_ONLY, ya obliga a usar TLS en todas las conexiones.
    # trivy:ignore:AVD-GCP-0015
    ip_configuration {
      ipv4_enabled    = false # sin IP pública
      private_network = google_compute_network.main.id
      ssl_mode        = "ENCRYPTED_ONLY"
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
      start_time                     = "04:00"
      backup_retention_settings {
        retained_backups = 7
      }
    }

    database_flags {
      name  = "log_min_duration_statement"
      value = "500" # registra consultas de más de 500 ms
    }

    insights_config {
      query_insights_enabled = true
    }
  }

  depends_on = [google_service_networking_connection.private_services]
}

resource "google_sql_database" "app" {
  name     = "interline"
  instance = google_sql_database_instance.main.name
}

# ---------------------------------------------------------------- contraseña sin pasar por el state
# `ephemeral` genera el valor solo durante el plan/apply; los argumentos `*_wo` (write-only) lo
# envían a la API sin guardarlo. Ni el state ni el plan contienen la contraseña.
ephemeral "random_password" "db" {
  length  = 32
  special = false
}

resource "google_secret_manager_secret" "db_password" {
  secret_id = "${local.prefix}-db-password"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret                 = google_secret_manager_secret.db_password.id
  secret_data_wo         = ephemeral.random_password.db.result
  secret_data_wo_version = var.db_password_version
}

resource "google_sql_user" "app" {
  name                = "interline"
  instance            = google_sql_database_instance.main.name
  password_wo         = ephemeral.random_password.db.result
  password_wo_version = var.db_password_version
}
