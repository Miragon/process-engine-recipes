"""Generates the HTML mockups of the central tasklist: three layout directions, three screens each."""
import html
import pathlib

OUT = pathlib.Path(__file__).parent

ICONS = {
    "search": '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
    "plus": '<path d="M5 12h14"/><path d="M12 5v14"/>',
    "chevron-down": '<path d="m6 9 6 6 6-6"/>',
    "chevron-right": '<path d="m9 18 6-6-6-6"/>',
    "x": '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
    "check": '<path d="M20 6 9 17l-5-5"/>',
    "alert": '<circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/>',
    "circle-x": '<circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/>',
    "clock": '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
    "calendar": '<rect width="18" height="18" x="3" y="4" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>',
    "user": '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    "users": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    "inbox": '<polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
    "history": '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l4 2"/>',
    "package": '<path d="m7.5 4.27 9 5.15"/><path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>',
    "card": '<rect width="20" height="14" x="2" y="5" rx="2"/><line x1="2" x2="22" y1="10" y2="10"/>',
    "bike": '<circle cx="18.5" cy="17.5" r="3.5"/><circle cx="5.5" cy="17.5" r="3.5"/><circle cx="15" cy="5" r="1"/><path d="M12 17.5V14l-3-3 4-3 2 3h2"/>',
    "bulb": '<path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/><path d="M9 18h6"/><path d="M10 22h4"/>',
    "helmet": '<path d="M10 10V5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5"/><path d="M14 6a6 6 0 0 1 6 6v3"/><path d="M4 15v-3a6 6 0 0 1 6-6"/><rect x="2" y="15" width="20" height="4" rx="1"/>',
    "lock": '<rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
    "flag": '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" x2="4" y1="22" y2="15"/>',
    "sort": '<path d="m21 16-4 4-4-4"/><path d="M17 20V4"/><path d="m3 8 4-4 4 4"/><path d="M7 4v16"/>',
    "more": '<circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/>',
    "panel": '<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/>',
}

KOMET = (OUT / "miragon-komet-blau.svg").read_text()


def icon(name, size=16, cls=""):
    return (f'<svg class="i {cls}" width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
            f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{ICONS[name]}</svg>')


def esc(text):
    return html.escape(text)


TASKS = [
    dict(key="1042", name="Check order", subject="Order ORD-1042 · Alice Smith · 129.90 EUR", domain="Orders", group="sales",
         assignee=None, prio="Normal", due="Nov 8", created="5 min ago"),
    dict(key="1017", name="Check payment", subject="Payment PAY-1017 · Order ORD-1039 · 89.00 EUR", domain="Payments", group="finance",
         assignee="Bob Baker", prio="High", due="Today", created="22 min ago"),
    dict(key="1036", name="Prepare delivery", subject="Order ORD-1036 · Jonas Weber · 2 items", domain="Orders", group="warehouse",
         assignee="Carol Clark", prio="Normal", due="Nov 9", created="1 h ago"),
    dict(key="1041", name="Check order", subject="Order ORD-1041 · Mia Fischer · 349.00 EUR", domain="Orders", group="sales",
         assignee="Alice Adams", prio="Normal", due="Nov 8", created="1 h ago"),
    dict(key="1016", name="Check payment", subject="Payment PAY-1016 · Order ORD-1035 · 1,948.90 EUR", domain="Payments", group="finance",
         assignee=None, prio="Normal", due="Nov 10", created="2 h ago"),
    dict(key="1040", name="Check order", subject="Order ORD-1040 · Leon Braun · 59.90 EUR", domain="Orders", group="sales",
         assignee=None, prio="Low", due="—", created="3 h ago"),
]


def initials(name):
    return "".join(part[0] for part in name.split())[:2]


def avatar(name):
    if not name:
        return '<span class="avatar avatar-empty" title="Unassigned">' + icon("user", 12) + "</span>"
    return f'<span class="avatar" title="{esc(name)}">{initials(name)}</span>'


def domain_chip(domain):
    return f'<span class="chip"><span class="dot dot-{domain.lower()}"></span>{esc(domain)}</span>'


def prio(value):
    if value == "High":
        return '<span class="badge badge-warning">' + icon("flag", 12) + "High</span>"
    return f'<span class="muted">{esc(value)}</span>'


def state_badge(state):
    if state == "Canceled":
        return '<span class="badge badge-danger">' + icon("circle-x", 12) + "Canceled</span>"
    if state == "Completed":
        return '<span class="badge badge-success">' + icon("check", 12) + "Completed</span>"
    return '<span class="badge badge-info">' + icon("clock", 12) + "Open</span>"


def user_switcher(name, group):
    return (f'<button class="user-switcher">{avatar(name)}<span><strong>{esc(name)}</strong>'
            f'<small>{esc(group)}</small></span>{icon("chevron-down", 14)}</button>')


def brand():
    return f'<a class="brand">{KOMET}<span>Central tasklist</span></a>'


def button(label, kind="primary", ic=None, disabled=False, small=False):
    cls = f"btn btn-{kind}" + (" btn-sm" if small else "")
    attr = " disabled" if disabled else ""
    return f'<button class="{cls}"{attr}>{icon(ic) if ic else ""}{esc(label)}</button>'


def canceled_box():
    return ('<div class="status status-danger" role="status">' + icon("circle-x", 18) +
            '<div><strong>This task was canceled by its process</strong>'
            '<p>The customer canceled order ORD-1042 while it was being checked. You can no longer work on this task.</p></div></div>')


def history(canceled):
    rows = [("Created", "by the Order process", "10:02"), ("Claimed", "by Alice Adams", "10:05")]
    if canceled:
        rows.append(("Canceled", "by the boundary event “Order canceled”", "10:09"))
    items = "".join(
        f'<li class="{"is-danger" if title == "Canceled" else ""}"><span class="tl-dot"></span>'
        f'<div><strong>{title}</strong> <span class="muted">{esc(who)}</span></div><time>{when}</time></li>'
        for title, who, when in rows)
    return f'<ol class="timeline">{items}</ol>'


def facts(rows):
    return '<dl class="facts">' + "".join(f"<div><dt>{esc(k)}</dt><dd>{v}</dd></div>" for k, v in rows) + "</dl>"


def order_frame(disabled=True):
    items = [("bulb", "Bike light set", "Front and rear, USB-C", 1, "49.90"), ("helmet", "Helmet Aero", "Size M, matte black", 1, "80.00")]
    rows = "".join(
        f'<tr><td><span class="item-icon">{icon(ic, 18)}</span></td><td><strong>{esc(n)}</strong><small>{esc(d)}</small></td>'
        f'<td class="num">{q}</td><td class="num">{p} EUR</td></tr>' for ic, n, d, q, p in items)
    dis = " disabled" if disabled else ""
    return f'''
<div class="frame">
  <div class="frame-label">{icon("lock", 12)} Form provided by <strong>Orders</strong> · check-order@1</div>
  <div class="frame-body">
    <div class="frame-head"><div><h3>Order ORD-1042</h3><p class="muted">Placed today 09:58 by Alice Smith</p></div>
      <div class="customer"><strong>Alice Smith</strong><span>alice.smith@example.com</span><span>Lindwurmstr. 12, 80337 München</span></div></div>
    <table class="items"><thead><tr><th></th><th>Item</th><th class="num">Qty</th><th class="num">Price</th></tr></thead>
      <tbody>{rows}</tbody><tfoot><tr><td></td><td>Total</td><td></td><td class="num">129.90 EUR</td></tr></tfoot></table>
    <fieldset class="field"><legend>Decision</legend>
      <div class="segmented{" is-disabled" if disabled else ""}"><label><input type="radio" name="d" checked{dis}> Approve</label><label><input type="radio" name="d"{dis}> Decline</label></div>
    </fieldset>
    <label class="field"><span>Comment</span><textarea rows="2"{dis} placeholder="Optional for approvals, required when you decline"></textarea></label>
  </div>
</div>'''


def payment_form():
    return f'''
<form class="jsonform">
  <section><h4>Invoice</h4>{facts([("Payment", "PAY-1017"), ("Order", "ORD-1039"), ("Customer", "Mia Fischer<br><span class='muted'>mia.fischer@example.com</span>"), ("Amount", "<strong>89.00 EUR</strong>")])}</section>
  <section><h4>Payment</h4>
    <fieldset class="field"><legend>Payment received?</legend>
      <div class="segmented"><label class="is-on"><input type="radio" name="r" checked> Yes</label><label><input type="radio" name="r"> No</label></div>
    </fieldset>
    <label class="field has-error"><span>Amount received (EUR)</span>
      <input value="80.00" aria-invalid="true">
      <small class="error">{icon("alert", 14)} The received amount must match the invoice amount of 89.00 EUR.</small></label>
    <label class="field"><span>Note <em>optional</em></span><textarea rows="2" placeholder="Visible to the next person working on this payment"></textarea></label>
  </section>
</form>'''


def head(title, css):
    return f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><title>{esc(title)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Geist:wght@100..900&family=Geist+Mono:wght@400;500&display=swap" rel="stylesheet">
<link rel="stylesheet" href="cd-tokens.generated.css"><link rel="stylesheet" href="base.css"><link rel="stylesheet" href="{css}">
</head><body>'''


def page(name, title, css, body):
    (OUT / f"{name}.html").write_text(head(title, css) + body + "</body></html>")


# ---------- Direction A: Split desk ----------

def a_topbar(user=("Alice Adams", "Sales")):
    return (f'<header class="topbar">{brand()}<label class="search">{icon("search")}<input placeholder="Search tasks, orders, payments"></label>'
            f'<div class="topbar-actions">{button("New order", "primary", "plus")}{user_switcher(*user)}</div></header>')


def a_rail():
    def group(title, items):
        lis = "".join(f'<li class="{"is-selected" if sel else ""}"><span>{esc(label)}</span><span class="count">{count}</span></li>'
                      for label, count, sel in items)
        return f"<section><h5>{title}</h5><ul>{lis}</ul></section>"
    return ('<nav class="rail">' +
            group("Assignment", [("All tasks", 17, False), ("Mine", 3, True), ("Unassigned", 8, False)]) +
            group("Domain", [("Orders", 12, False), ("Payments", 5, False)]) +
            group("State", [("Open", 17, True), ("Completed", 42, False), ("Canceled", 4, False)]) +
            group("Candidate group", [("sales", 9, False), ("finance", 5, False), ("warehouse", 3, False)]) + "</nav>")


def a_list(selected):
    rows = "".join(
        f'''<li class="row{" is-selected" if t["key"] == selected else ""}">
  <div class="row-main"><div class="row-title"><strong>{esc(t["name"])}</strong>{prio(t["prio"]) if t["prio"] == "High" else ""}</div>
  <div class="row-sub">{esc(t["subject"])}</div>
  <div class="row-meta">{domain_chip(t["domain"])}<span class="muted">{icon("users", 12)} {t["group"]}</span><span class="muted">{icon("calendar", 12)} {t["due"]}</span></div></div>
  <div class="row-side">{avatar(t["assignee"])}<time class="muted">{t["created"]}</time></div></li>''' for t in TASKS)
    return (f'<section class="list"><div class="list-head"><h2>Open tasks <span class="muted">17</span></h2>'
            f'<button class="btn btn-ghost btn-sm">{icon("sort", 14)} Newest first</button></div><ul class="rows">{rows}</ul></section>')


def a_detail_overview():
    t = TASKS[0]
    return f'''<section class="detail">
  <div class="detail-head"><div><p class="eyebrow">{domain_chip("Orders")} <span class="muted">Order · ORD-1042</span></p>
  <h1>{esc(t["name"])}</h1><p class="muted">{esc(t["subject"])}</p></div>{state_badge("Open")}</div>
  {facts([("Candidate group", "sales"), ("Assignee", "<span class='muted'>Unassigned</span>"), ("Priority", "Normal"), ("Due", "Nov 8, 2026"), ("Created", "Today 10:02")])}
  <div class="empty-form">{icon("inbox", 22)}<p><strong>Claim the task to work on its form.</strong><br><span class="muted">Only the assignee can complete it.</span></p>{button("Claim task", "primary")}</div>
</section>'''


def a_detail_form():
    return f'''<section class="detail">
  <div class="detail-head"><div><p class="eyebrow">{domain_chip("Payments")} <span class="muted">Payment · PAY-1017</span></p>
  <h1>Check payment</h1><p class="muted">Payment PAY-1017 · Order ORD-1039 · 89.00 EUR</p></div>{state_badge("Open")}</div>
  {facts([("Candidate group", "finance"), ("Assignee", avatar("Bob Baker") + " Bob Baker (you)"), ("Priority", prio("High")), ("Due", "Today, 17:00")])}
  <div class="card">{payment_form()}</div>
  <footer class="actionbar">{button("Unclaim", "secondary")}<span class="spacer"></span><span class="muted small">{icon("alert", 14)} 1 field needs attention</span>{button("Complete task", "primary", "check")}</footer>
</section>'''


def a_detail_canceled():
    return f'''<section class="detail">
  <div class="detail-head"><div><p class="eyebrow">{domain_chip("Orders")} <span class="muted">Order · ORD-1042</span></p>
  <h1>Check order</h1><p class="muted">Order ORD-1042 · Alice Smith · 129.90 EUR</p></div>{state_badge("Canceled")}</div>
  {canceled_box()}
  {order_frame()}
  <footer class="actionbar">{button("Unclaim", "secondary", disabled=True)}<span class="spacer"></span><span class="muted small">{icon("lock", 14)} Read-only: the task was canceled</span>{button("Complete task", "primary", "check", disabled=True)}</footer>
  <h4 class="section-title">{icon("history", 14)} History</h4>{history(True)}
</section>'''


def direction_a():
    page("a-list", "A · Split desk · list", "a.css", f'<div class="app">{a_topbar()}<div class="panes">{a_rail()}{a_list("1042")}{a_detail_overview()}</div></div>')
    page("a-form", "A · Split desk · form", "a.css", f'<div class="app">{a_topbar(("Bob Baker", "Finance"))}<div class="panes">{a_rail()}{a_list("1017")}{a_detail_form()}</div></div>')
    page("a-canceled", "A · Split desk · canceled", "a.css", f'<div class="app">{a_topbar()}<div class="panes">{a_rail()}{a_list("1042")}{a_detail_canceled()}</div></div>')


# ---------- Direction B: Ledger ----------

def b_header(user=("Alice Adams", "Sales"), tab="Open"):
    tabs = "".join(f'<a class="tab{" is-active" if label == tab else ""}">{label} <span class="count">{count}</span></a>'
                   for label, count in [("Open", 17), ("Completed", 42), ("Canceled", 4)])
    return (f'<header class="b-header">{brand()}<nav class="tabs">{tabs}</nav>'
            f'<div class="topbar-actions"><label class="search">{icon("search")}<input placeholder="Search"></label>{button("New order", "primary", "plus")}{user_switcher(*user)}</div></header>')


def b_filters():
    chips = [("Domain", "All"), ("Group", "sales"), ("Assigned", "Me or unassigned"), ("Process", "All"), ("Priority", "Any")]
    html_chips = "".join(f'<button class="filter-chip{" is-set" if value not in ("All", "Any") else ""}"><span class="muted">{k}:</span> {esc(value)} {icon("chevron-down", 12)}</button>' for k, value in chips)
    return f'<div class="filters">{html_chips}<button class="btn btn-ghost btn-sm">Clear filters</button><span class="spacer"></span><span class="muted small">Updated live</span></div>'


def b_table(hover=None):
    rows = "".join(
        f'''<tr class="{"is-hover" if t["key"] == hover else ""}"><td><strong>{esc(t["name"])}</strong></td><td class="muted">{esc(t["subject"])}</td>
<td>{domain_chip(t["domain"])}</td><td>{t["group"]}</td><td>{(avatar(t["assignee"]) + " " + esc(t["assignee"])) if t["assignee"] else '<span class="muted">Unassigned</span>'}</td>
<td>{prio(t["prio"])}</td><td class="num">{t["due"]}</td><td class="num muted">{t["created"]}</td></tr>''' for t in TASKS)
    cols = "".join(f"<th{' class=num' if c in ('Due', 'Created') else ''}>{c}{icon('sort', 12) if c == 'Created' else ''}</th>"
                   for c in ["Task", "Subject", "Domain", "Candidate group", "Assignee", "Priority", "Due", "Created"])
    return (f'<div class="table-card"><table class="ledger"><thead><tr>{cols}</tr></thead><tbody>{rows}</tbody></table>'
            f'<footer class="table-foot"><span class="muted">1–6 of 17</span><span class="spacer"></span>{button("Previous", "secondary", small=True, disabled=True)}{button("Next", "secondary", small=True)}</footer></div>')


def b_drawer(title, eyebrow, subject, state, body, footer):
    tabs = '<div class="drawer-tabs"><a class="is-active">Form</a><a>Details</a><a>History <span class="count">3</span></a></div>'
    return f'''<div class="backdrop"></div><aside class="drawer">
  <header class="drawer-head"><div><p class="eyebrow">{eyebrow}</p><h2>{esc(title)}</h2><p class="muted">{esc(subject)}</p></div>
  <div class="drawer-head-side">{state_badge(state)}<button class="btn btn-icon" aria-label="Close">{icon("x")}</button></div></header>
  {tabs}<div class="drawer-body">{body}</div><footer class="drawer-foot">{footer}</footer></aside>'''


def direction_b():
    page("b-list", "B · Ledger · list", "b.css", f'<div class="app">{b_header()}<main class="b-main">{b_filters()}{b_table(hover="1041")}</main></div>')
    form_body = f'{facts([("Candidate group", "finance"), ("Assignee", "Bob Baker (you)"), ("Priority", prio("High")), ("Due", "Today, 17:00")])}<div class="card">{payment_form()}</div>'
    form_foot = f'{button("Unclaim", "secondary")}<span class="spacer"></span><span class="muted small">{icon("alert", 14)} 1 field needs attention</span>{button("Complete task", "primary", "check")}'
    page("b-form", "B · Ledger · form", "b.css", f'<div class="app">{b_header(("Bob Baker", "Finance"))}<main class="b-main">{b_filters()}{b_table()}</main>'
         + b_drawer("Check payment", domain_chip("Payments") + ' <span class="muted">Payment · PAY-1017</span>', "Payment PAY-1017 · Order ORD-1039 · 89.00 EUR", "Open", form_body, form_foot) + "</div>")
    canceled_body = canceled_box() + order_frame()
    canceled_foot = f'{button("Unclaim", "secondary", disabled=True)}<span class="spacer"></span><span class="muted small">{icon("lock", 14)} Read-only: the task was canceled</span>{button("Complete task", "primary", "check", disabled=True)}'
    page("b-canceled", "B · Ledger · canceled", "b.css", f'<div class="app">{b_header()}<main class="b-main">{b_filters()}{b_table()}</main>'
         + b_drawer("Check order", domain_chip("Orders") + ' <span class="muted">Order · ORD-1042</span>', "Order ORD-1042 · Alice Smith · 129.90 EUR", "Canceled", canceled_body, canceled_foot) + "</div>")


# ---------- Direction C: Workbench ----------

def c_topbar(user=("Alice Adams", "Sales")):
    return f'<header class="c-top">{brand()}<span class="spacer"></span>{button("New order", "secondary", "plus")}{user_switcher(*user)}</header>'


def c_queue(selected):
    def card(t):
        return (f'<li class="qcard{" is-selected" if t["key"] == selected else ""}"><div class="qcard-top"><strong>{esc(t["name"])}</strong>'
                f'<time class="muted">{t["created"]}</time></div><p>{esc(t["subject"])}</p>'
                f'<div class="qcard-meta"><span class="muted">{icon("users", 12)} {t["group"]}</span>{prio(t["prio"]) if t["prio"] == "High" else ""}{avatar(t["assignee"])}</div></li>')
    orders = "".join(card(t) for t in TASKS if t["domain"] == "Orders")
    payments = "".join(card(t) for t in TASKS if t["domain"] == "Payments")
    return f'''<aside class="queue"><label class="search">{icon("search")}<input placeholder="Search the queue"></label>
  <div class="segmented seg-full"><label class="is-on">My tasks</label><label>My groups</label><label>All</label></div>
  <section><h5>{icon("package", 14)} Orders <span class="count">4</span></h5><ul>{orders}</ul></section>
  <section><h5>{icon("card", 14)} Payments <span class="count">2</span></h5><ul>{payments}</ul></section></aside>'''


def c_task_side(state, assignee, canceled=False):
    return (f'<aside class="c-side"><div class="card"><h4>Task</h4>' +
            facts([("State", state_badge(state)), ("Process", "Order · v3"), ("Business ID", "<span class='mono'>ORD-1042</span>"),
                   ("Candidate group", "sales"), ("Assignee", assignee), ("Priority", "Normal"), ("Due", "Nov 8, 2026")]) +
            f'</div><div class="card"><h4>{icon("history", 14)} History</h4>{history(canceled)}</div></aside>')


def direction_c():
    overview = f'''<main class="c-main"><div class="c-title"><div><p class="eyebrow">{domain_chip("Orders")} <span class="muted">Order process</span></p><h1>Check order</h1>
<p class="muted">Order ORD-1042 · Alice Smith · 129.90 EUR</p></div>{button("Claim task", "primary")}</div>
<div class="c-grid"><div class="card empty-form">{icon("lock", 22)}<p><strong>Claim the task to open its form.</strong><br><span class="muted">The order review is provided by the Orders application.</span></p></div>
{c_task_side("Open", "<span class='muted'>Unassigned</span>")}</div></main>'''
    page("c-list", "C · Workbench · list", "c.css", f'<div class="app">{c_topbar()}<div class="c-body">{c_queue("1042")}{overview}</div></div>')
    form_main = f'''<main class="c-main"><div class="c-title"><div><p class="eyebrow">{domain_chip("Payments")} <span class="muted">Payment process</span></p><h1>Check payment</h1>
<p class="muted">Payment PAY-1017 · Order ORD-1039 · 89.00 EUR</p></div><div class="c-actions">{button("Unclaim", "secondary")}{button("Complete task", "primary", "check")}</div></div>
<div class="c-grid"><div class="card">{payment_form()}</div>
<aside class="c-side"><div class="card"><h4>Task</h4>{facts([("State", state_badge("Open")), ("Process", "Payment · v2"), ("Business ID", "<span class='mono'>PAY-1017</span>"), ("Candidate group", "finance"), ("Assignee", "Bob Baker (you)"), ("Priority", prio("High")), ("Due", "Today, 17:00")])}</div>
<div class="card"><h4>{icon("history", 14)} History</h4><ol class="timeline"><li><span class="tl-dot"></span><div><strong>Created</strong> <span class="muted">by the Payment process</span></div><time>09:41</time></li><li><span class="tl-dot"></span><div><strong>Claimed</strong> <span class="muted">by Bob Baker</span></div><time>09:58</time></li></ol></div></aside></div></main>'''
    page("c-form", "C · Workbench · form", "c.css", f'<div class="app">{c_topbar(("Bob Baker", "Finance"))}<div class="c-body">{c_queue("1017")}{form_main}</div></div>')
    canceled_main = f'''<main class="c-main">{canceled_box()}<div class="c-title"><div><p class="eyebrow">{domain_chip("Orders")} <span class="muted">Order process</span></p><h1>Check order</h1>
<p class="muted">Order ORD-1042 · Alice Smith · 129.90 EUR</p></div><div class="c-actions">{button("Unclaim", "secondary", disabled=True)}{button("Complete task", "primary", "check", disabled=True)}</div></div>
<div class="c-grid"><div>{order_frame()}</div>{c_task_side("Canceled", avatar("Alice Adams") + " Alice Adams", canceled=True)}</div></main>'''
    page("c-canceled", "C · Workbench · canceled", "c.css", f'<div class="app">{c_topbar()}<div class="c-body">{c_queue("1042")}{canceled_main}</div></div>')


if __name__ == "__main__":
    direction_a()
    direction_b()
    direction_c()
    print("written:", sorted(p.name for p in OUT.glob("*.html")))
