# Combat 1.0.6 compatibility fix

The inspected Alora client implements `Client.menuAction(...)` by adding a menu
entry and posting `MenuEntryAdded`. It does not execute that entry. This explains
the repeated `Attacking Yak` log messages without combat in 1.0.5.

The Alora adapter constructs an entry and invokes the normal click handler,
which posts `MenuOptionClicked` and respects consumption. The inspected entry
and dispatcher classes are SHA-256 checked before resolving the method by its
signature. A changed client stops with an explicit compatibility error instead
of invoking an unverified obfuscated method. Other clients retain the existing
menu-action path.

Both the loader and Combat script must be updated, followed by a full client
restart. Combat displays `Attack requested` on dispatch and `In combat` when
the player has an interaction. A dispatch return does not prove server acceptance.

The optional `io.runeforge.tests.AloraCompatibilityTest` takes the path to a
locally supplied client JAR. It checks adapter resolution and rejection of changed
bytecode without launching a game session. No private client classes are bundled.

Live target interaction still needs verification in the running game.
