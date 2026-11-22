{{- define "app.labels" -}}
app.kubernetes.io/part-of: datn
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
datn/layer: app
{{- end }}

{{/* Nhãn cho pod template: không có phiên bản chart */}}
{{- define "app.podLabels" -}}
app.kubernetes.io/part-of: datn
datn/layer: app
{{- end }}

{{/* dict "root" $ "name" "<service>"  ->  ghcr.io/<owner>/datn-<service>:<tag> */}}
{{- define "app.image" -}}
{{ .root.Values.image.registry }}/datn-{{ .name }}:{{ required "image.tag là bắt buộc" .root.Values.image.tag }}
{{- end }}
