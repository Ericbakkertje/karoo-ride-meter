# Karoo Ride Meter — testversie 1.2.0

Een grafisch dataveld voor de Hammerhead Karoo 3 (werkt volgens een tester ook op de Karoo 2; niet op de Karoo 1): snelheid op een wijzerplaat met cijfers,
versnelling en accu van de elektronische schakeling (SRAM AXS, of Shimano Di2 via Ki2),
afstand of gemiddelde snelheid, en drie ronde velden naar keuze.

Bestand: `KarooRideMeter-1.2.0.apk`
SHA-256: `e44fb67507b6cf764be994ad4ba5ca49c6451b8a85b3ceac3d03860b30ff3244`

## Nieuw in 1.2.0

- **Licht/donker:** de meter kleurt mee met de lichte of donkere modus van de Karoo. In de lichte modus met fellere kleuren.
- **Kleuren in de donkere modus:** kies in de instellingen tussen normaal (neon) en fel.
- **Boven de wijzerplaat zelf kiezen:** links en rechts staan standaard de accu en de versnelling. Rijd je mechanisch,
  kies dan temperatuur, helling, hoogte, hoogtemeters, afstand tot finish, of niets (dan schuift de wijzerplaat omhoog).
- **AXS FULL** in plaats van AXS NEW bij een volle accu.
- **Grotere letters** bij accu en versnelling.

## Installeren

**Zonder computer (via je telefoon, getest met iPhone):**
1. Open de releasepagina op je telefoon.
2. Houd je vinger op `KarooRideMeter-1.2.0.apk` en kies **Delen** → de **Hammerhead**-app.
3. Zorg dat de Karoo aan staat en met je telefoon verbonden is; bevestig de installatie op de Karoo.

**Met een computer (adb):**
1. Zet op de Karoo ontwikkelaarsopties en USB-debugging aan, en sluit hem aan.
2. `adb install -r KarooRideMeter-1.2.0.apk`

Een update over 1.1.x heen houdt je instellingen en velden.

## Gebruiken

1. Bewerk een pagina in een ritprofiel en voeg een veld toe: **Ride Meter → Ride Meter**.
   Het werkt het best als groot veld (volle pagina of halve pagina).
2. Open de app **Ride Meter Settings** in de app-lijst van de Karoo om in te stellen:
   - de schaal van de wijzerplaat (0–30, 0–45, 0–60 of 0–90 km/h),
   - wat er onder de snelheid staat (afstand of gemiddelde snelheid),
   - wat er linksboven en rechtsboven staat,
   - de kleuren in de donkere modus,
   - wat de drie ronde velden tonen (helling, cadans, hartslag, vermogen, enz.).

## Schakeling (beide getest)

- **SRAM AXS:** werkt via de Karoo zelf, als de AXS als sensor gekoppeld is.
- **Shimano Di2:** installeer de extensie **Ki2** en koppel de Di2 daarin. Voeg de Di2 daarna in de
  Karoo toe als sensor (**Sensors → + → Extensions → Ki2**) en stel de tandjes in, anders blijft de
  versnelling leeg.

## Feedback

Laat graag weten wat je ziet: de veldgrootte, wat er niet klopt, en zo mogelijk een schermafbeelding.
