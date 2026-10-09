locals {
  prefix = "interline-${var.environment}"
  images = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}

# ---------------------------------------------------------------- Artifact Registry
resource "google_artifact_registry_repository" "images" {
  repository_id = "interline"
  location      = var.region
  format        = "DOCKER"
  description   = "Imágenes de Interline Booking Manager (backend, frontend, sandbox del proveedor)"

  docker_config {
    immutable_tags = true # un tag (el SHA del commit) nunca se reescribe
  }

  cleanup_policy_dry_run = false

  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = 15
    }
  }

  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"
    condition {
      older_than = "2592000s" # 30 días (las 15 más recientes se conservan siempre)
    }
  }
}

# CI publica imágenes en este repositorio (y en ningún otro).
resource "google_artifact_registry_repository_iam_member" "deployer_writer" {
  repository = google_artifact_registry_repository.images.name
  location   = var.region
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${var.deployer_service_account}"
}
