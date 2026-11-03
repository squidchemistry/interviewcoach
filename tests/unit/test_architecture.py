"""
Guard the clean-architecture dependency rule: inner layers (domain,
application) must not import outer layers or frameworks.
"""

import ast
from pathlib import Path

import pytest

APP = Path(__file__).resolve().parents[2] / "app"

FORBIDDEN = {
    "domain": ("app.application", "app.infrastructure", "app.api", "fastapi", "sqlalchemy", "groq"),
    "application": ("app.infrastructure", "app.api", "fastapi", "sqlalchemy", "groq"),
}


def imported_modules(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    names: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            names.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module:
            names.add(node.module)
    return names


@pytest.mark.parametrize("layer", sorted(FORBIDDEN))
def test_inner_layers_do_not_depend_on_outer_layers(layer):
    violations = []
    for path in (APP / layer).rglob("*.py"):
        for module in imported_modules(path):
            if module.startswith(FORBIDDEN[layer]):
                violations.append(f"{path.relative_to(APP)} imports {module}")
    assert violations == []
