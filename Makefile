.PHONY: deploy

deploy:
	k3d cluster delete vela; k3d cluster create vela; ./Deploy.sh --build

run:
	./Deploy.sh

delete:
	kubectl delete -f infra/kubernetes/App/Deployment.yaml --ignore-not-found

helm-delete:
	helm delete dockerpoc || true

helm-install:
	helm install dockerpoc ./infra/helm/dockerpoc-app

port-forward:
	kubectl port-forward svc/ingress-nginx-controller 8080:80 -n ingress-nginx

helm-redeploy:
	helm delete dockerpoc || true
	helm install dockerpoc ./infra/helm/dockerpoc-app
	kubectl port-forward svc/ingress-nginx-controller 8080:80 -n ingress-nginx