{{- define "app.labels" -}}
app.kubernetes.io/part-of: datn
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
datn/layer: app
{{- end }}

{{/* Nhãn cho pod template: không có phiên bản chart, để nâng chart không tự restart pod */}}
{{- define "app.podLabels" -}}
app.kubernetes.io/part-of: datn
datn/layer: app
{{- end }}

{{/* dict "root" $ "name" "<service>"  ->  docker.io/<user>/datn-<service>:<tag> */}}
{{- define "app.image" -}}
{{ required "global.registry là bắt buộc (infrastructure/helm/env-minikube.yaml)" .root.Values.global.registry }}/datn-{{ .name }}:{{ required "image.tag là bắt buộc (CI truyền --set image.tag=<git-sha>)" .root.Values.image.tag }}
{{- end }}

{{/* URL công khai của ứng dụng và của Keycloak, ví dụ https://app.example.com */}}
{{- define "app.publicUrl" -}}
{{ .Values.global.scheme }}://{{ .Values.global.appHost }}
{{- end }}

{{- define "app.ssoUrl" -}}
{{ .Values.global.scheme }}://{{ .Values.global.ssoHost }}
{{- end }}

{{/* Cookie Secure chỉ bật khi đi qua HTTPS (trình duyệt không gửi cookie Secure qua http) */}}
{{- define "app.cookieSecure" -}}
{{ eq .Values.global.scheme "https" }}
{{- end }}
