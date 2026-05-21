{{/*
Common template helpers
*/}}

{{- define "dockerpoc-app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{- define "dockerpoc-app.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "dockerpoc-app.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end }}

{{- define "dockerpoc-app.labels" -}}
app.kubernetes.io/name: {{ include "dockerpoc-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end }}

{{- define "dockerpoc-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dockerpoc-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "dockerpoc-app.image" -}}
{{- printf "%s:%s" .Values.deployment.container.image .Values.deployment.container.tag -}}
{{- end }}

{{- define "dockerpoc-app.imagePullSecrets" -}}
{{- $secrets := list }}
{{- if .Values.global.imagePullSecrets }}{{- $secrets = concat $secrets .Values.global.imagePullSecrets }}{{- end }}
{{- if .Values.imagePullSecrets }}{{- $secrets = concat $secrets .Values.imagePullSecrets }}{{- end }}
{{- if $secrets }}
imagePullSecrets:
{{- range $secrets }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{- define "dockerpoc-app.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "dockerpoc-app.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end }}


{{- define "dockerpoc-app.pvcName" -}}
{{- default (printf "%s-pvc" (include "dockerpoc-app.fullname" .)) .Values.persistence.pvc.name -}}
{{- end }}

{{- define "dockerpoc-app.pvName" -}}
{{- default (printf "%s-pv" (include "dockerpoc-app.fullname" .)) .Values.persistence.pv.name -}}
{{- end }}

{{- define "dockerpoc-app.storageClassName" -}}
{{- default .Values.storageClass.name .Values.persistence.storageClass -}}
{{- end }}

{{/* API helpers to keep manifests portable */}}
{{- define "dockerpoc-app.deployment.apiVersion" -}}
apps/v1
{{- end }}

{{- define "dockerpoc-app.ingress.apiVersion" -}}
{{- if semverCompare ">=1.19-0" .Capabilities.KubeVersion.GitVersion -}}
networking.k8s.io/v1
{{- else -}}
networking.k8s.io/v1beta1
{{- end -}}
{{- end }}

{{/* Simple values validation hook (optional) */}}
{{- define "dockerpoc-app.validateValues" -}}
{{- if and (not .Values.autoscaling.enabled) (lt (int .Values.replicaCount) 1) -}}
{{- fail "replicaCount must be >= 1 when autoscaling is disabled" -}}
{{- end -}}
{{- end }}

{{- define "dockerpoc-app.ingressName" -}}
{{- default (include "dockerpoc-app.fullname" .) .Values.ingress.name -}}
{{- end }}

{{- define "dockerpoc-app.deploymentComponentLabel" -}}
{{- if .Values.deployment.componentLabel -}}
app.kubernetes.io/component: {{ .Values.deployment.componentLabel }}
{{- end -}}
{{- end }}

