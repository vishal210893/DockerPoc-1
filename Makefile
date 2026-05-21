.PHONY: deploy run build delete helm-delete helm-install helm-package helm-redeploy port-forward

# Full build + deploy cycle on a fresh k3d cluster.
deploy:
	k3d cluster delete vela; k3d cluster create vela; ./Deploy.sh --build

# Deploy the currently-tagged image without rebuilding.
run:
	./Deploy.sh

# Build, push, and patch manifests + Helm values — no deploy.
build:
	./Deploy.sh --build-only

delete:
	kubectl delete -f infra/kubernetes/App/Deployment.yaml --ignore-not-found

helm-delete:
	helm delete dockerpoc || true

helm-install:
	helm install dockerpoc ./infra/helm/dockerpoc-app

# Lint the chart and write a versioned .tgz package into ./dist
helm-package:
	helm lint ./infra/helm/dockerpoc-app
	mkdir -p dist
	helm package ./infra/helm/dockerpoc-app -d dist

helm-redeploy:
	helm delete dockerpoc || true
	helm install dockerpoc ./infra/helm/dockerpoc-app
	kubectl port-forward svc/ingress-nginx-controller 8005:80 -n ingress-nginx

port-forward:
	kubectl port-forward svc/ingress-nginx-controller 8005:80 -n ingress-nginx
