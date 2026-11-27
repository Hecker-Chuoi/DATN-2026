{{- define "platform.labels" -}}
app.kubernetes.io/part-of: datn
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
datn/layer: platform
{{- end }}

{{/* Nhãn cho pod template: KHÔNG có phiên bản chart, để nâng chart không làm restart DB */}}
{{- define "platform.podLabels" -}}
app.kubernetes.io/part-of: datn
datn/layer: platform
{{- end }}

{{/* Áp cho mọi pod của platform */}}
{{- define "platform.podDefaults" -}}
enableServiceLinks: false            # BẮT BUỘC cho Kafka: Service tên "kafka" sẽ sinh biến KAFKA_PORT=tcp://... làm image apache/kafka hỏng
automountServiceAccountToken: false  # hạ tầng không cần gọi API Kubernetes
{{- end }}

{{/* URL công khai của Keycloak, ví dụ https://sso.example.com */}}
{{- define "platform.ssoUrl" -}}
{{ .Values.global.scheme }}://{{ .Values.global.ssoHost }}
{{- end }}

{{/* Tham chiếu một key của Secret app-secrets: include "platform.secretKey" (dict "root" $ "key" "app-db-password") */}}
{{- define "platform.secretKey" -}}
secretKeyRef:
  name: {{ .root.Values.secretName }}
  key: {{ .key }}
{{- end }}
