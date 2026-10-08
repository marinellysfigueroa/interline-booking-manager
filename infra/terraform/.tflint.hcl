# tflint con el ruleset "terraform" incluido (convenciones, variables sin uso, tipos...).
config {
  call_module_type = "none"
}

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
