/*
 * Copyright 2020 White Magic Software, Ltd.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.scrivenvar.editors;

import com.scrivenvar.FileEditorTab;
import com.scrivenvar.sigils.SigilOperator;
import com.scrivenvar.definition.DefinitionPane;
import com.scrivenvar.definition.DefinitionTreeItem;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.StyledTextArea;

import java.nio.file.Path;
import java.text.BreakIterator;

import static javafx.scene.input.KeyCode.SPACE;
import static javafx.scene.input.KeyCombination.CONTROL_DOWN;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;

/**
 * Provides the logic for injecting variable names within the editor.
 */
public final class DefinitionNameInjector {

  /**
   * Recipient of name injections.
   */
  private FileEditorTab mTab;

  /**
   * Initiates double-click events.
   */
  private final DefinitionPane mDefinitionPane;

  /**
   * Initializes the variable name injector against the given pane.
   *
   * @param pane The definition panel to listen to for double-click events.
   */
  public DefinitionNameInjector( final DefinitionPane pane ) {
    mDefinitionPane = pane;
  }

  /**
   * Trap Control+Space.
   *
   * @param tab Editor where variable names get injected.
   */
  public void addListener( final FileEditorTab tab ) {
    assert tab != null;
    mTab = tab;

    tab.getEditorPane().addKeyboardListener(
        keyPressed( SPACE, CONTROL_DOWN ),
        this::autoinsert
    );
  }

  /**
   * Inserts the currently selected variable from the {@link DefinitionPane}.
   */
  public void injectSelectedItem() {
    final var pane = getDefinitionPane();
    final TreeItem<String> item = pane.getSelectedItem();

    if( item.isLeaf() ) {
      final var leaf = pane.findLeafExact( item.getValue() );
      final var editor = getEditor();

      editor.insertText( editor.getCaretPosition(), decorate( leaf ) );
    }
  }

  /**
   * Pressing Control+SPACE will find a node that matches the current word and
   * substitute the YAML variable reference.
   *
   * @param e Ignored -- it can only be Control+SPACE.
   */
  private void autoinsert( final KeyEvent e ) {
    final String paragraph = getCaretParagraph();
    final int[] boundaries = getWordBoundariesAtCaret();
    final String word = paragraph.substring( boundaries[ 0 ], boundaries[ 1 ] );
    final DefinitionTreeItem<String> leaf = findLeaf( word );

    if( leaf != null ) {
      replaceText( boundaries[ 0 ], boundaries[ 1 ], decorate( leaf ) );
      expand( leaf );
    }
  }

  private int[] getWordBoundariesAtCaret() {
    final String paragraph = getCaretParagraph();
    int offset = getCurrentCaretColumn();

    final BreakIterator wordBreaks = BreakIterator.getWordInstance();
    wordBreaks.setText( paragraph );

    // Scan back until the first word is found.
    while( offset > 0 && wordBreaks.isBoundary( offset ) ) {
      offset--;
    }

    final int[] boundaries = new int[ 2 ];
    boundaries[ 1 ] = wordBreaks.following( offset );
    boundaries[ 0 ] = wordBreaks.previous();

    return boundaries;
  }

  /**
   * Decorates a {@link TreeItem} using the syntax specific to the type of
   * document being edited.
   *
   * @param leaf The path to the leaf (the definition key) to be decorated.
   */
  private String decorate( final DefinitionTreeItem<String> leaf ) {
    return decorate( leaf.toPath() );
  }

  /**
   * Decorates a variable using the syntax specific to the type of document
   * being edited.
   *
   * @param variable The variable to decorate in dot-notation without any
   *                 start or end sigils present.
   */
  private String decorate( final String variable ) {
    return getVariableDecorator().apply( variable );
  }

  /**
   * Updates the text at the given position within the current paragraph.
   *
   * @param posBegan The starting index in the paragraph text to replace.
   * @param posEnded The ending index in the paragraph text to replace.
   * @param text     Overwrite the paragraph substring with this text.
   */
  private void replaceText(
      final int posBegan, final int posEnded, final String text ) {
    final int p = getCurrentParagraph();

    getEditor().replaceText( p, posBegan, p, posEnded, text );
  }

  /**
   * Returns the caret's current paragraph position.
   *
   * @return A number greater than or equal to 0.
   */
  private int getCurrentParagraph() {
    return getEditor().getCurrentParagraph();
  }

  /**
   * Returns the text for the paragraph that contains the caret.
   *
   * @return A non-null string, possibly empty.
   */
  private String getCaretParagraph() {
    return getEditor().getText( getCurrentParagraph() );
  }

  /**
   * Returns the caret position within the current paragraph.
   *
   * @return A value from 0 to the length of the current paragraph.
   */
  private int getCurrentCaretColumn() {
    return getEditor().getCaretColumn();
  }

  /**
   * Looks for the given word, matching first by exact, next by a starts-with
   * condition with diacritics replaced, then by containment.
   *
   * @param word The word to match by: exact, at the beginning, or containment.
   * @return The matching {@link DefinitionTreeItem} for the given word, or
   * {@code null} if none found.
   */
  @SuppressWarnings("ConstantConditions")
  private DefinitionTreeItem<String> findLeaf( final String word ) {
    assert word != null;

    final var pane = getDefinitionPane();
    DefinitionTreeItem<String> leaf = null;

    leaf = leaf == null ? pane.findLeafExact( word ) : leaf;
    leaf = leaf == null ? pane.findLeafStartsWith( word ) : leaf;
    leaf = leaf == null ? pane.findLeafContains( word ) : leaf;
    leaf = leaf == null ? pane.findLeafContainsNoCase( word ) : leaf;

    return leaf;
  }

  /**
   * Collapses the tree then expands and selects the given node.
   *
   * @param node The node to expand.
   */
  private void expand( final TreeItem<String> node ) {
    final DefinitionPane pane = getDefinitionPane();
    pane.collapse();
    pane.expand( node );
    pane.select( node );
  }

  /**
   * @return A variable decorator that corresponds to the given file type.
   */
  private SigilOperator getVariableDecorator() {
    return VariableNameDecoratorFactory.newInstance( getFilename() );
  }

  private Path getFilename() {
    return getFileEditorTab().getPath();
  }

  private EditorPane getEditorPane() {
    return getFileEditorTab().getEditorPane();
  }

  private StyledTextArea<?, ?> getEditor() {
    return getEditorPane().getEditor();
  }

  public FileEditorTab getFileEditorTab() {
    return mTab;
  }

  private DefinitionPane getDefinitionPane() {
    return mDefinitionPane;
  }
}
