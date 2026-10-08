mock_provider "google" {
  mock_resource "google_service_account" {
    defaults = {
      name  = "projects/test-project/serviceAccounts/mock-sa@test-project.iam.gserviceaccount.com"
      email = "mock-sa@test-project.iam.gserviceaccount.com"
    }
  }
  mock_resource "google_iam_workload_identity_pool" {
    defaults = {
      name = "projects/123/locations/global/workloadIdentityPools/github"
    }
  }
}

variables {
  project_id        = "test-project"
  github_repository = "marinellysfigueroa/interline-booking-manager"
}

run "only_this_repository_can_federate" {
  command = plan

  assert {
    condition     = google_iam_workload_identity_pool_provider.github.attribute_condition == "assertion.repository == 'marinellysfigueroa/interline-booking-manager'"
    error_message = "El proveedor WIF debe limitarse al repositorio."
  }
}

run "deploy_and_apply_only_from_main" {
  command = apply

  assert {
    condition     = endswith(google_service_account_iam_member.deployer_wif.member, "/attribute.repository_ref/marinellysfigueroa/interline-booking-manager@refs/heads/main")
    error_message = "Solo la rama main puede desplegar."
  }
  assert {
    condition     = endswith(google_service_account_iam_member.terraform_wif.member, "@refs/heads/main")
    error_message = "Solo la rama main puede aplicar Terraform."
  }
  assert {
    condition     = google_storage_bucket.tfstate.versioning[0].enabled && google_storage_bucket.tfstate.public_access_prevention == "enforced"
    error_message = "El bucket del state debe estar versionado y sin acceso público."
  }
}

run "no_owner_or_editor_roles" {
  command = plan

  assert {
    condition = alltrue([
      for m in concat(values(google_project_iam_member.terraform), values(google_project_iam_member.terraform_plan), [google_project_iam_member.deployer_run]) :
      !contains(["roles/owner", "roles/editor"], m.role)
    ])
    error_message = "Las cuentas de CI no deben tener roles primitivos."
  }
}
