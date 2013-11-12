/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

/*
Copyright Christophe Cerisara, Josselin Pierre (1er septembre 2008)

cerisara@loria.fr

Ce logiciel est un programme informatique servant � aligner un
corpus de parole avec sa transcription textuelle.

Ce logiciel est r�gi par la licence CeCILL-C soumise au droit fran�ais et
respectant les principes de diffusion des logiciels libres. Vous pouvez
utiliser, modifier et/ou redistribuer ce programme sous les conditions
de la licence CeCILL-C telle que diffus�e par le CEA, le CNRS et l'INRIA 
sur le site "http://www.cecill.info".

En contrepartie de l'accessibilit� au code source et des droits de copie,
de modification et de redistribution accord�s par cette licence, il n'est
offert aux utilisateurs qu'une garantie limit�e.  Pour les m�mes raisons,
seule une responsabilit� restreinte p�se sur l'auteur du programme,  le
titulaire des droits patrimoniaux et les conc�dants successifs.

A cet �gard  l'attention de l'utilisateur est attir�e sur les risques
associ�s au chargement,  � l'utilisation,  � la modification et/ou au
d�veloppement et � la reproduction du logiciel par l'utilisateur �tant 
donn� sa sp�cificit� de logiciel libre, qui peut le rendre complexe � 
manipuler et qui le r�serve donc � des d�veloppeurs et des professionnels
avertis poss�dant  des  connaissances  informatiques approfondies.  Les
utilisateurs sont donc invit�s � charger  et  tester  l'ad�quation  du
logiciel � leurs besoins dans des conditions permettant d'assurer la
s�curit� de leurs syst�mes et ou de leurs donn�es et, plus g�n�ralement, 
� l'utiliser et l'exploiter dans les m�mes conditions de s�curit�. 

Le fait que vous puissiez acc�der � cet en-t�te signifie que vous avez 
pris connaissance de la licence CeCILL-C, et que vous en avez accept� les
termes.
*/

package plugins.text.elements;

/**
 * Wrapper autour d'un commentaire
 */
public class Element_Commentaire implements Element {
	public static final long serialVersionUID = 2;

	public final String comment;
	public int posDebInTextPanel,posFinInTextPanel;

	public Element_Commentaire(String comment, int posdeb) {
		this.comment = comment;
		this.posDebInTextPanel = posdeb;
		this.posFinInTextPanel = posdeb + comment.length();
	}

	public Element_Commentaire(String comment, int posdeb, int posfin) {
		this.comment = comment;
		this.posDebInTextPanel = posdeb;
		this.posFinInTextPanel = posfin;
	}

	/**
	 * Returns an Element_Mot whose word is created from a substring of
	 * bigString, beginning at posdeb and ending at posfin-1.
	 */
	public static Element_Commentaire fromSubstring(String bigString, int posdeb, int posfin) {
		return new Element_Commentaire(bigString.substring(posdeb, posfin), posdeb, posfin);
	}
}//class Element_Commentaire
