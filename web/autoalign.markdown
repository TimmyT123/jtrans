---
categories: Doc g�n�rale
date: 2013/04/06 15:25:00
title: Comment faire un alignement automatique
---
Tout commence par le bouton "AutoAlign", qui se trouve dans une instance
de la classe "ControlBox", instanci�e dans la classe principale Aligneur.java

L'objet qui fait l'alignement est de la classe "AutoAligner"
Cet objet lance un thread (cf. method "run") qui r�alise l'alignement et qui
est tu� lorsqu'on appuie sur le bouton "StopIt", ce qui lance la method "stopAutoAlign"

Dans Aligneur, la var autoAligner contient les alignements en cours.

