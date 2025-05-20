.PHONY: deploy run

deploy:
	k3d cluster delete vela; k3d cluster create vela; ./Deploy.sh --build

run:
	./Deploy.sh