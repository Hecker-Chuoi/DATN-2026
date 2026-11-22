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
enableServiceLinks: false            # BẮT BUỘC cho Kafka (01 §5.3): không có biến KAFKA_PORT=tcp://... do Service "kafka" sinh ra
automountServiceAccountToken: false
{{- end }}

{{/* Ingress công cụ quản trị: basic auth + (tuỳ chọn) giới hạn IP */}}
{{- define "platform.adminIngressAnnotations" -}}
nginx.ingress.kubernetes.io/auth-type: basic
nginx.ingress.kubernetes.io/auth-secret: {{ .Values.adminAccess.basicAuthSecret }}
nginx.ingress.kubernetes.io/auth-realm: "DATN - khu vuc quan tri"
{{- with .Values.adminAccess.allowCidrs }}
nginx.ingress.kubernetes.io/whitelist-source-range: {{ . | quote }}
{{- end }}
{{- end }}
