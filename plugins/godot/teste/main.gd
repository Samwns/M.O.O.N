extends Control
func _ready() -> void:
	var l: Label = $Label
	l.text = "JOGO EXTERNO CARREGADO\n.pck de PC rodando no motor Godot %s\nrenderer: %s\nautoload /root/Estado = %s" % [Engine.get_version_info().string, RenderingServer.get_video_adapter_name(), get_node("/root/Estado").name]
	print("JOGO_EXTERNO: ", l.text.replace("\n", " | "))
