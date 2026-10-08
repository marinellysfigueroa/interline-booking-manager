# Red privada: Cloud SQL solo tiene IP privada y Cloud Run llega a ella con Direct VPC egress
# (sin conector serverless, sin coste fijo).
resource "google_compute_network" "main" {
  name                    = "${local.prefix}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "run" {
  name          = "${local.prefix}-run"
  network       = google_compute_network.main.id
  region        = var.region
  ip_cidr_range = var.subnet_cidr

  # Necesario para que el frontend (egress ALL_TRAFFIC por la VPC) alcance el backend, cuyo
  # ingress es interno, a través de su URL *.run.app.
  private_ip_google_access = true
}

# Private Services Access: rango reservado para la IP privada de Cloud SQL.
resource "google_compute_global_address" "private_services" {
  name          = "${local.prefix}-psa"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 20
  network       = google_compute_network.main.id
}

resource "google_service_networking_connection" "private_services" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]
}
