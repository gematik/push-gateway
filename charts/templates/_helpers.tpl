{{/*
Expand the name of the chart.
*/}}
{{- define "push-gateway.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "push-gateway.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "push-gateway.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "push-gateway.labels" -}}
helm.sh/chart: {{ include "push-gateway.chart" . }}
{{ include "push-gateway.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{ define "push-gateway.producer.labels" -}}
{{ include "push-gateway.labels" . }}
app.kubernetes.io/component: producer
{{ end -}}

{{ define "push-gateway.consumer.labels" -}}
{{ include "push-gateway.labels" . }}
app.kubernetes.io/component: consumer
{{ end -}}

{{ define "push-gateway.broker.labels" -}}
{{ include "push-gateway.labels" . }}
app.kubernetes.io/component: broker
{{ end -}}


{{/*
Selector labels
*/}}
{{- define "push-gateway.selectorLabels" -}}
app.kubernetes.io/name: {{ include "push-gateway.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{ define "push-gateway.producer.selectorLabels" -}}
{{ include "push-gateway.selectorLabels" . }}
app.kubernetes.io/component: producer
{{ end -}}

{{ define "push-gateway.consumer.selectorLabels" -}}
{{ include "push-gateway.selectorLabels" . }}
app.kubernetes.io/component: consumer
{{ end -}}

{{ define "push-gateway.broker.selectorLabels" -}}
{{ include "push-gateway.selectorLabels" . }}
app.kubernetes.io/component: broker
{{ end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "push-gateway.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "push-gateway.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
