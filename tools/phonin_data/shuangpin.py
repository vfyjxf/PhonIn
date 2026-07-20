"""Shuangpin (双拼) encoders, ported from the RIME double-pinyin schema algebras
(https://github.com/rime/rime-double-pinyin, BSD-3-Clause).

Each scheme is expressed as RIME `algebra` rules (erase / derive / xform / xlit).
`encode(syllable, scheme)` applies them in order to a toneless pinyin syllable (with
ü written as 'v') and returns the 2-key code. Because the same algebra drives both
encoding and the expected-set index, every shuangpin test case is internally consistent.

Schemes: flypy (小鹤), zrm (自然码), mspy (微软), pyjj (加加), abc (智能ABC).
"""

import re

_ALGEBRAS = {
    "flypy": """
- erase/^xx$/
- derive/^([jqxy])u$/$1v/
- derive/^([aoe])([ioun])$/$1$1$2/
- xform/^([aoe])(ng)?$/$1$1$2/
- xform/iu$/Q/
- xform/(.)ei$/$1W/
- xform/uan$/R/
- xform/[uv]e$/T/
- xform/un$/Y/
- xform/^sh/U/
- xform/^ch/I/
- xform/^zh/V/
- xform/uo$/O/
- xform/ie$/P/
- xform/i?ong$/S/
- xform/ing$|uai$/K/
- xform/(.)ai$/$1D/
- xform/(.)en$/$1F/
- xform/(.)eng$/$1G/
- xform/[iu]ang$/L/
- xform/(.)ang$/$1H/
- xform/ian$/M/
- xform/(.)an$/$1J/
- xform/(.)ou$/$1Z/
- xform/[iu]a$/X/
- xform/iao$/N/
- xform/(.)ao$/$1C/
- xform/ui$/V/
- xform/in$/B/
- xlit/QWRTYUIOPSDFGHJKLZXCVBNM/qwrtyuiopsdfghjklzxcvbnm/
""",
    "zrm": """
- erase/^xx$/
- derive/^([jqxy])u$/$1v/
- derive/^([aoe])([ioun])$/$1$1$2/
- xform/^([aoe])(ng)?$/$1$1$2/
- xform/iu$/Q/
- xform/[iu]a$/W/
- xform/[uv]an$/R/
- xform/[uv]e$/T/
- xform/ing$|uai$/Y/
- xform/^sh/U/
- xform/^ch/I/
- xform/^zh/V/
- xform/uo$/O/
- xform/[uv]n$/P/
- xform/i?ong$/S/
- xform/[iu]ang$/D/
- xform/(.)en$/$1F/
- xform/(.)eng$/$1G/
- xform/(.)ang$/$1H/
- xform/ian$/M/
- xform/(.)an$/$1J/
- xform/iao$/C/
- xform/(.)ao$/$1K/
- xform/(.)ai$/$1L/
- xform/(.)ei$/$1Z/
- xform/ie$/X/
- xform/ui$/V/
- xform/(.)ou$/$1B/
- xform/in$/N/
- xlit/QWRTYUIOPSDFGHMJCKLZXVBN/qwrtyuiopsdfghmjcklzxvbn/
""",
    "mspy": """
- erase/^xx$/
- derive/^([jqxy])u$/$1v/
- derive/^([aoe].*)$/o$1/
- xform/^([ae])(.*)$/$1$1$2/
- xform/iu$/Q/
- xform/[iu]a$/W/
- xform/er$|[uv]an$/R/
- xform/[uv]e$/T/
- xform/v$|uai$/Y/
- xform/^sh/U/
- xform/^ch/I/
- xform/^zh/V/
- xform/uo$/O/
- xform/[uv]n$/P/
- xform/i?ong$/S/
- xform/[iu]ang$/D/
- xform/(.)en$/$1F/
- xform/(.)eng$/$1G/
- xform/(.)ang$/$1H/
- xform/ian$/M/
- xform/(.)an$/$1J/
- xform/iao$/C/
- xform/(.)ao$/$1K/
- xform/(.)ai$/$1L/
- xform/(.)ei$/$1Z/
- xform/ie$/X/
- xform/ui$/V/
- derive/T$/V/
- xform/(.)ou$/$1B/
- xform/in$/N/
- xform/ing$/;/
- xlit/QWRTYUIOPSDFGHMJCKLZXVBN/qwrtyuiopsdfghmjcklzxvbn/
""",
    "pyjj": """
- erase/^xx$/
- derive/^([jqxy])u$/$1v/
- derive/^([aoe].*)$/o$1/
- xform/^([ae])(.*)$/$1$1$2/
- xform/iu$/N/
- xform/[iu]a$/B/
- xform/er$|ing$/Q/
- xform/[uv]an$/C/
- xform/[uv]e$|uai$/X/
- xform/^sh/I/
- xform/^ch/U/
- xform/^zh/V/
- xform/uo$/O/
- xform/[uv]n$/Z/
- xform/i?ong$/Y/
- xform/[iu]ang$/H/
- xform/(.)en$/$1R/
- xform/(.)eng$/$1T/
- xform/(.)ang$/$1G/
- xform/ian$/J/
- xform/(.)an$/$1F/
- xform/iao$/K/
- xform/(.)ao$/$1D/
- xform/(.)ai$/$1S/
- xform/(.)ei$/$1W/
- xform/ie$/M/
- xform/ui$/V/
- xform/(.)ou$/$1P/
- xform/in$/L/
- xlit/QWRTYUIOPSDFGHMJCKLZXVBN/qwrtyuiopsdfghmjcklzxvbn/
""",
    "abc": """
- erase/^xx$/
- derive/^([jqxy])u$/$1v/
- xform/^zh/A/
- xform/^ch/E/
- xform/^sh/V/
- xform/^([aoe].*)$/O$1/
- xform/ei$/Q/
- xform/ian$/W/
- xform/er$|iu$/R/
- xform/[iu]ang$/T/
- xform/ing$/Y/
- xform/uo$/O/
- xform/uan$/P/
- xform/i?ong$/S/
- xform/[iu]a$/D/
- xform/en$/F/
- xform/eng$/G/
- xform/ang$/H/
- xform/an$/J/
- xform/iao$/Z/
- xform/ao$/K/
- xform/in$|uai$/C/
- xform/ai$/L/
- xform/ie$/X/
- xform/ou$/B/
- xform/un$/N/
- xform/[uv]e$|ui$/M/
- xlit/QWERTYOPASDFGHJKLZXCVBNM/qwertyopasdfghjklzxcvbnm/
""",
}

SCHEMES = ("flypy", "zrm", "mspy", "pyjj", "abc")
SCHEME_NAMES = {
    "flypy": "小鹤双拼",
    "zrm": "自然码",
    "mspy": "微软双拼",
    "pyjj": "拼音加加",
    "abc": "智能ABC",
}


def _parse(text):
    rules = []
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("- "):
            continue
        body = line[2:].strip()
        op = body.split("/", 1)[0]
        rest = body[len(op) + 1:]
        parts = rest.split("/")
        if op in ("xform", "derive"):
            pat = parts[0]
            repl = parts[1] if len(parts) > 1 else ""
            repl = repl.replace("$", "\\")
            rules.append(("sub", pat, repl))
        elif op == "erase":
            rules.append(("erase", parts[0]))
        elif op == "xlit":
            rules.append(("xlit", parts[0], parts[1] if len(parts) > 1 else ""))
    return rules


_RULES = {name: _parse(text) for name, text in _ALGEBRAS.items()}


def encode(syllable, scheme):
    """Encode a toneless pinyin syllable (ü='v') to a 2-key shuangpin code."""
    s = syllable
    for rule in _RULES[scheme]:
        if rule[0] == "erase":
            if re.search(rule[1], s):
                return None
        elif rule[0] == "sub":
            s = re.sub(rule[1], rule[2], s)
        elif rule[0] == "xlit":
            s = s.translate(str.maketrans(rule[1], rule[2]))
    return s
