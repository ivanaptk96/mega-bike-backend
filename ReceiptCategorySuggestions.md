# Predlozi kategorija pri prijemu robe

Predlog se vraća uz svaku stavku započetog prijema kao `categorySuggestion`. Sadrži ID kategorije, celu putanju, izvor, obrazloženje i `requiresSelection`. Neodređen artikal ima `null` predlog i zahteva ručni izbor.

## Redosled prepoznavanja

1. Kategorija već povezanog artikla (`EXISTING_PRODUCT`). Prijem ne menja kategoriju tog artikla.
2. Potvrđeno povezivanje dobavljača i šifre (`SAVED_SUPPLIER_CODE`).
3. Potvrđeno povezivanje dobavljača i barkoda (`SAVED_BARCODE`). Barkod trenutno dolazi iz uvezenog artikla dobavljača, jer SEF stavka nema posebno barkod polje.
4. Izričito potvrđeno mapiranje dobavljačeve kategorije (`SAVED_SUPPLIER_CATEGORY`).
5. Pravila za naziv (`NAME_RULE`); tačan, jednoznačan naziv/putanja kategorije dobavljača može dopuniti nepotpun predlog (`SUPPLIER_CATEGORY`).

Sačuvana povezivanja koja više ne označavaju aktivnu, vidljivu krajnju kategoriju preskaču se. Izbori se ne prenose na drugog dobavljača.

## Potvrda i pamćenje

Za novi artikal predložena krajnja kategorija odmah je odabrana u padajućem meniju, uz blago isticanje i obrazloženje dostupno pri prelasku pokazivačem. Zaposleni može promeniti izbor u istom meniju; nema posebne kutije niti dugmeta za prihvatanje predloga. Nepotpun predlog prikazuje odgovarajuću grupu i nudi samo krajnje kategorije ispod nje, uz dugme za prikaz svih kategorija. Nepotpun predlog nije moguće prihvatiti kao krajnju kategoriju.

Uspešna potvrda prijema automatski pamti konačnu kategoriju po šifri i dostupnom barkodu dobavljača, zajedno sa korisnikom i vremenom potvrde. Samo gledanje ili promena izbora u formi ne upisuje mapiranje.

Opciona postavka `rememberSupplierCategory` na stavci potvrde uči mapiranje cele kategorije dobavljača. U formi je podrazumevano isključena i prikazuje se za nove artikle kada je izvorna kategorija dostupna. Ne uči se šira dobavljačeva grupa na osnovu jednog artikla bez tog izbora. Konfliktna mapiranja iste grupe u istoj potvrdi odbijaju celu potvrdu.

Pamćenje, povezivanje artikla i knjiženje robe su deo iste transakcije. Neuspešna potvrda ne ostavlja kategorijska povezivanja ili novoformirane artikle.

## Katalog dobavljača i obavezna cena

Uvoz kataloga Planet Bike ili Capriolo čuva izvorne kategorije dobavljača, ali ne kreira naše artikle i ne dodeljuje im našu kategoriju. Predlog se prikazuje pri prijemu; konačna kategorija novog artikla upisuje se tek uspešnom potvrdom prijema.

Prodajna cena mora biti uneta i ispravna pre potvrde prijema. Obrazac označava stavku sa nedostajućom ili neispravnom cenom i fokusira prvo problematično polje. Provera važi i pri grupnom unosu cena. Informativna prodajna cena sa sajta dobavljača nije nabavna cena niti automatski potvrđena naša prodajna cena.

## Primeri pravila

| Naziv | Predlog |
| --- | --- |
| GUR-GUR MAGNESIUM / GURALICA POLAR FUZZY | Bicikli / Dečiji / Balans (guralice) |
| BIC. POLAR JR 14 Dino green | Bicikli / Dečiji / 14″ |
| Unutrašnja guma 29 AV | Delovi / Gume / Unutrašnje / 29″ / AV |
| Unutrašnja guma 29 | Delovi / Gume / Unutrašnje / 29″ — traži ventil |
| Lula volana | Delovi / Volan / Lula volana |
| Lanac za MTB bicikl | Delovi / Pogon / Lanac |
| Pumpa za bicikl MTB | Oprema / Pumpe za bicikle |

Pravila zanemaruju razliku velikih/malih slova i dijakritike. Podržavaju decimalni zarez u dimenziji 27,5. Više različitih dimenzija ili ventila ostavlja predlog nepotpunim. ETRTO i metričke dimenzije ne pretvaraju se automatski u dimenzije točka. Ne postoji AI servis niti spoljašnji poziv.

Nova Liquibase migracija `inventory/006-receipt-category-mappings.xml` kreira tabelu pri sledećem pokretanju backenda. Provere su rađene testovima na zasebnoj PostgreSQL bazi; lokalne aplikacije nisu pokretane.
