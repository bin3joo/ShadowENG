"""StyleEcho root entrypoint."""

import importlib

_module_name = f"{__package__}.api.app" if __package__ else "api.app"
_app_module = importlib.import_module(_module_name)
app = _app_module.app
create_app = _app_module.create_app
