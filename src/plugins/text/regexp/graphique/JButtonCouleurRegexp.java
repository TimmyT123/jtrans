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

package plugins.text.regexp.graphique;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JOptionPane;

import plugins.text.regexp.TypeElement;

public class JButtonCouleurRegexp extends JButton implements ActionListener {

	//------ private Fields -------
	private TypeElement typeElement;
	
	
	//-------- Constructor -------
	public JButtonCouleurRegexp(TypeElement typeElement){
		this.typeElement = typeElement;
		setBackground(typeElement.getColor());
		setText("Couleur");
		addActionListener(this);
	}//constructor
	
	public void actionPerformed(ActionEvent e){
		SelecteurCouleur colorPanel = new SelecteurCouleur();
		colorPanel.setColor(getBackground());
		
		Object[] options = {"Editer","Annuler"};

		int selectedOption = JOptionPane.showOptionDialog(null,
				colorPanel,
				"R�glage des couleurs",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.PLAIN_MESSAGE,
				null,     //do not use a custom Icon
				options,  //the titles of buttons
				options[0]); //default button title
		
		if(selectedOption == JOptionPane.YES_OPTION){
			typeElement.setColor(colorPanel.getColor());
			setBackground(colorPanel.getColor());
		}//if
	}//actionPerformed
	
}//JButton
