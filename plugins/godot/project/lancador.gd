extends Control
# Lancador do plugin M.O.O.N Godot (GDScript, sem Java nosso).
#
# Por que existe: os templates Android OFICIAIS do Godot sao compilados sem
# "path overrides", entao "--main-pack /sdcard/..." e recusado (main/main.cpp,
# "--main-pack is attempting to load from outside of the executable"), e o extra
# "command_line_params" e descartado em atividades exportadas (GodotActivity.kt,
# sanitizeLaunchIntent). O que o motor aceita e ProjectSettings.load_resource_pack().
# Ele nao recarrega as configuracoes do projeto, entao refazemos aqui o que o
# Main::setup faria: ler res://project.binary (formato ECFG) do .pck, aplicar as
# configuracoes, instanciar os autoloads e trocar para a cena principal.
#
# Entrada: extra de Intent "moon_pck" (caminho do .pck).

func _ready() -> void:
	var rotulo := Label.new()
	rotulo.text = "M.O.O.N Godot " + Engine.get_version_info().string
	add_child(rotulo)
	var pck := _pck_do_intent()
	if pck == "":
		rotulo.text += "\nSem jogo: abra pelo M.O.O.N (extra moon_pck)."
		return
	rotulo.text += "\n" + pck
	if ProjectSettings.get_setting("moon/motor_proprio", false) or OS.has_feature("moon_motor"):
		call_deferred("_reiniciar", pck, rotulo)
	else:
		call_deferred("_carregar", pck, rotulo)

# Motor proprio (disable_path_overrides=no): reabre o motor com --main-pack e todos os autoloads.
func _reiniciar(pck: String, rotulo: Label) -> void:
	if not FileAccess.file_exists(pck):
		rotulo.text += "\nERRO: arquivo nao existe (permissao de armazenamento?)"
		return
	var pid := OS.create_instance(["--main-pack", pck])
	print("MOON: create_instance --main-pack ", pck, " pid/res=", pid)

func _pck_do_intent() -> String:
	if OS.has_environment("MOON_PCK"):
		return OS.get_environment("MOON_PCK")
	if not Engine.has_singleton("AndroidRuntime"):
		return ""
	var act = Engine.get_singleton("AndroidRuntime").getActivity()
	var v = act.getIntent().getStringExtra("moon_pck")
	return "" if v == null else str(v)

func _carregar(pck: String, rotulo: Label) -> void:
	if not FileAccess.file_exists(pck):
		rotulo.text += "\nERRO: arquivo nao existe (permissao de armazenamento?)"
		print("MOON: pck inexistente: ", pck)
		return
	if not ProjectSettings.load_resource_pack(pck, true):
		rotulo.text += "\nERRO: load_resource_pack falhou"
		print("MOON: load_resource_pack falhou")
		return
	print("MOON: pck carregado: ", pck)
	var cfg := _ler_ecfg("res://project.binary")
	print("MOON: ", cfg.size(), " configuracoes lidas do pck")
	for k in cfg:
		ProjectSettings.set_setting(k, cfg[k])
	InputMap.load_from_project_settings()
	_aplicar_janela()
	var raiz := get_tree().root
	for k in cfg:
		if str(k).begins_with("autoload/"):
			var caminho: String = str(cfg[k]).trim_prefix("*")
			var nome: String = str(k).trim_prefix("autoload/")
			var recurso = load(caminho)
			var no: Node = recurso.new() if recurso is GDScript else recurso.instantiate()
			no.name = nome
			raiz.add_child(no)
			# os scripts seguintes citam o autoload pelo nome: registra como singleton
			Engine.register_singleton(nome, no)
			print("MOON: autoload ", nome)
	var cena: String = str(cfg.get("application/run/main_scene", ""))
	if cena == "":
		rotulo.text += "\nERRO: o pck nao define cena principal"
		return
	print("MOON: cena principal ", cena)
	get_tree().change_scene_to_file(cena)

func _aplicar_janela() -> void:
	var j := get_window()
	var w := int(ProjectSettings.get_setting("display/window/size/viewport_width", 1152))
	var h := int(ProjectSettings.get_setting("display/window/size/viewport_height", 648))
	j.content_scale_size = Vector2i(w, h)
	match str(ProjectSettings.get_setting("display/window/stretch/mode", "disabled")):
		"canvas_items": j.content_scale_mode = Window.CONTENT_SCALE_MODE_CANVAS_ITEMS
		"viewport": j.content_scale_mode = Window.CONTENT_SCALE_MODE_VIEWPORT
	match str(ProjectSettings.get_setting("display/window/stretch/aspect", "keep")):
		"expand": j.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_EXPAND
		"keep_width": j.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_KEEP_WIDTH
		"keep_height": j.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_KEEP_HEIGHT
		"ignore": j.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_IGNORE
		_: j.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_KEEP

# project.binary: "ECFG", u32 n, n x { u32 len, chave utf8, u32 len, Variant codificado }
func _ler_ecfg(caminho: String) -> Dictionary:
	var r := {}
	var f := FileAccess.open(caminho, FileAccess.READ)
	if f == null:
		return r
	if f.get_buffer(4).get_string_from_ascii() != "ECFG":
		return r
	var n := f.get_32()
	for i in n:
		var chave := f.get_buffer(f.get_32()).get_string_from_utf8()
		var d := f.get_buffer(f.get_32())
		r[chave] = bytes_to_var(d)
	return r
