# Karoo Ride Meter — testversie 1.1.2

Een grafisch dataveld voor de Hammerhead Karoo 3: snelheid op een wijzerplaat met cijfers,
versnelling en accu van de elektronische schakeling (SRAM AXS, of Shimano Di2 via Ki2),
afstand of gemiddelde snelheid, en drie ronde velden naar keuze.

Bestand: `KarooRideMeter-1.1.2.apk`
SHA-256: `ba5bae9be36d07d54f4969212e1dc8a8a0785152fdc2a7cfd6a012a22e2b2199`

## Installeren

**Met een computer (adb):**
1. Zet op de Karoo ontwikkelaarsopties en USB-debugging aan, en sluit hem aan.
2. `adb install KarooRideMeter-1.1.2.apk`

**Zonder computer:** dit zou moeten kunnen door de downloadlink van de APK te delen met de
Hammerhead-app op je telefoon, die hem dan op de Karoo zet (nog niet door ons getest).

## Gebruiken

1. Bewerk een pagina in een ritprofiel en voeg een veld toe: **Ride Meter → Ride Meter**.
   Het werkt het best als groot veld (volle pagina of halve pagina).
2. Open de app **Ride Meter Settings** in de app-lijst van de Karoo om in te stellen:
   - de schaal van de wijzerplaat (0–30, 0–45, 0–60 of 0–90 km/h),
   - wat er onder de snelheid staat (afstand of gemiddelde snelheid),
   - wat de drie ronde velden tonen (helling, cadans, hartslag, vermogen, enz.).

## Schakeling

- **SRAM AXS:** werkt via de Karoo zelf, als de AXS als sensor gekoppeld is.
- **Shimano Di2:** installeer de extensie **Ki2** en koppel de Di2 daarin. Voeg de Di2 daarna in de
  Karoo toe als sensor (**Sensors → + → Extensions → Ki2**) en stel de tandjes in, anders blijft de
  versnelling leeg.

## Feedback

Laat graag weten wat je ziet: de veldgrootte, wat er niet klopt, en zo mogelijk een schermafbeelding.
