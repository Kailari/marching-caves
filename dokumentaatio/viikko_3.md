Viikko 3.
---------
*Työmäärä tällä viikolla ~17 tuntia*

Tiheysfunktio alkaa vähitellen muodostua. Päänvaivaa aiheuttaa pakkomielteeni löytää "järkevä" tai "oikea" tapa laskea polun kontribuutio tiheysfunktioon. Haluaisin voida määrittää solmuittain parametreja tiheysfunktioille ja laskea varsinaiset kaarien tiheyskontribuutiot interpoloimalla näiden välillä, mutta toistaiseksi se on osoittautunut haastavaksi. Hieman oikean suuntaista toteutusta alkaa olla kasassa, mutta en ole vielä siihen aivan tyytyväinen nykyisellään.

Polun kontribuution lisäksi aloin parannella tiheysfunktion toimintaa. Tarkasteltavan pisteen ja polun lähimmän pisteen välistä pistetuloa käyttäen oli melko triviaalia "tasoittaa" luolan lattiaa. Aiemmin mainittu solmujen välillä interpolointi kuitenkin aiheuttaa jonkin verran "aaltoilua" luolan muotoon joka ei näytä tämän kanssa hyvältä.

Ongelma kuitenkin oikeastaan ratkaisi itse itsensä kun lisäsin *Simplex Noise* -algoritmilla ylimääräisen kerroksen kohinaa polun muotoon. Lyhyesti, generoin 3-uloitteista kohinaa, joka skaalataan kyllin suureksi että muodot ovat melko pyöreitä. Tämän jälkeen erinäisiä parametreja käyttäen, kerrostetaan tämä kohina-arvo luolan tiheysarvon kanssa. Lopputuloksena luolan seinät eivät enää ole suorat, vaan niissä on hyvin vaihtelevia muotoja, tehden luolasta paljon mielenkiintoisemman.

Seuraavaksi todennäköisesti seurava iteraatio tiheysfunktioiden välillä interpoloinnista ja kohinafunktion kerrostamista fraktaalikohinaksi.
