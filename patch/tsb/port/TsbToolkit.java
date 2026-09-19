package tsb.port;

/**
 * Das Toolkit, das es auf dem Telefon sonst nicht gibt.
 *
 * <h2>Warum es das braucht</h2>
 *
 * <p>{@code java.awt.Toolkit.getDefaultToolkit()} liest die Eigenschaft {@code awt.toolkit}
 * und laedt die genannte Klasse:
 *
 * <pre>
 * String nm = System.getProperty("awt.toolkit");
 * cls = Class.forName(nm);
 * </pre>
 *
 * <p>Auf einem Desktop-JDK setzt der Starter diese Eigenschaft. Auf MobiVM und auf Android
 * setzt sie niemand — {@code nm} ist {@code null}, und {@code Class.forName(null)} wirft eine
 * NullPointerException. Genau daran hielt der Swing-Port an, und zwar tief in
 * {@code MetalLookAndFeel.getDefaults()} ueber {@code SwingUtilities2.getSystemMnemonicKeyMask}.
 *
 * <p>Eingekreist wurde das mit einer isolierten Sonde: eine <i>fehlende</i> Klasse wirft bei
 * MobiVM {@code ClassNotFoundException} in {@code VMClassLoader.findClassInClasspathForLoader},
 * ein <i>null-Name</i> wirft {@code NullPointerException} in {@code Class.classForName} — und
 * der zweite Rahmen war es. Zwei andere Verdachtsmomente (fehlender Kontextlader, null-Lader)
 * waren vorher widerlegt worden.
 *
 * <h2>Warum fast alles wirft</h2>
 *
 * <p>Von den 45 abstrakten Methoden sind fast alle Peer-Fabriken fuer schwergewichtige
 * AWT-Bausteine — {@code createButton}, {@code createFrame}, {@code createScrollbar}. Swing
 * benutzt davon <b>keine einzige</b>: es ist leichtgewichtig und malt sich selbst. Gebraucht
 * wird ein Peer nur fuer das oberste Fenster, und das gibt es hier nicht — die Zeichenflaeche
 * ist der Bildschirm.
 *
 * <p>Deshalb werfen sie, <b>mit ihrem Namen in der Meldung</b>, statt still etwas
 * zurueckzugeben. Dieselbe Haltung wie beim {@code Entnativisierer}: was doch gebraucht wird,
 * soll sich melden, damit die Liste waechst statt das Raetsel.
 */
public class TsbToolkit extends java.awt.Toolkit
        implements sun.awt.KeyboardFocusManagerPeerProvider {

    /**
     * tsbMobile: {@code KeyboardFocusManager.initPeer} castet das Toolkit auf diese
     * Schnittstelle — ohne sie: {@code ClassCastException}, und Swing kommt nicht ueber
     * {@code UIManager.initialize} hinaus.
     *
     * <p>Der Peer bleibt vorerst {@code null}. Fokus wird gebraucht, sobald es Texteingabe
     * gibt; bis dahin soll sich melden, wer ihn anfasst, statt dass ein Scheinobjekt still
     * das Falsche tut.
     */
    @Override
    public java.awt.peer.KeyboardFocusManagerPeer getKeyboardFocusManagerPeer() {
        return null;
    }


    /** Eine Warteschlange, mehr braucht Swing von hier nicht. */
    private final java.awt.EventQueue warteschlange = new java.awt.EventQueue();

    @Override
    protected java.awt.peer.DesktopPeer createDesktopPeer(java.awt.Desktop a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createDesktopPeer wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.ButtonPeer createButton(java.awt.Button a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createButton wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.TextFieldPeer createTextField(java.awt.TextField a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createTextField wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.LabelPeer createLabel(java.awt.Label a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createLabel wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.ListPeer createList(java.awt.List a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createList wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.CheckboxPeer createCheckbox(java.awt.Checkbox a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createCheckbox wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.ScrollbarPeer createScrollbar(java.awt.Scrollbar a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createScrollbar wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.ScrollPanePeer createScrollPane(java.awt.ScrollPane a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createScrollPane wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.TextAreaPeer createTextArea(java.awt.TextArea a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createTextArea wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.ChoicePeer createChoice(java.awt.Choice a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createChoice wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.FramePeer createFrame(java.awt.Frame a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createFrame wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.CanvasPeer createCanvas(java.awt.Canvas a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createCanvas wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.PanelPeer createPanel(java.awt.Panel a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createPanel wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.WindowPeer createWindow(java.awt.Window a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createWindow wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.DialogPeer createDialog(java.awt.Dialog a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createDialog wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.MenuBarPeer createMenuBar(java.awt.MenuBar a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createMenuBar wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.MenuPeer createMenu(java.awt.Menu a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createMenu wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.PopupMenuPeer createPopupMenu(java.awt.PopupMenu a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createPopupMenu wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.MenuItemPeer createMenuItem(java.awt.MenuItem a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createMenuItem wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.FileDialogPeer createFileDialog(java.awt.FileDialog a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createFileDialog wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.CheckboxMenuItemPeer createCheckboxMenuItem(java.awt.CheckboxMenuItem a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createCheckboxMenuItem wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.peer.FontPeer getFontPeer(java.lang.String a0, int a1) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getFontPeer wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.Dimension getScreenSize() throws java.awt.HeadlessException {
        return new java.awt.Dimension(390, 844);
    }

    @Override
    public int getScreenResolution() throws java.awt.HeadlessException {
        return 160;
    }

    @Override
    public java.awt.image.ColorModel getColorModel() throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getColorModel wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.lang.String[] getFontList() {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getFontList wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    /**
     * tsbMobile: dieselbe Antwort wie {@code SwingUtilities2.getFontMetrics}.
     *
     * <p>Swing nimmt diesen Weg, wenn keine Komponente zur Hand ist. Beide Wege muessen
     * dieselbe Metrik liefern, sonst misst dieselbe Maske je nach Aufrufer verschieden.
     */
    @Override
    public java.awt.FontMetrics getFontMetrics(java.awt.Font a0) {
        return Schrift.metrik(a0);
    }

    @Override
    public void sync() {
        // nichts zu tun: es gibt keinen Fensterserver, der hinterherhinken koennte
    }

    @Override
    public java.awt.Image getImage(java.lang.String a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.Image getImage(java.net.URL a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.Image createImage(java.lang.String a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.Image createImage(java.net.URL a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public boolean prepareImage(java.awt.Image a0, int a1, int a2, java.awt.image.ImageObserver a3) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.prepareImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public int checkImage(java.awt.Image a0, int a1, int a2, java.awt.image.ImageObserver a3) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.checkImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.Image createImage(java.awt.image.ImageProducer a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.Image createImage(byte[] a0, int a1, int a2) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createImage wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.awt.PrintJob getPrintJob(java.awt.Frame a0, java.lang.String a1, java.util.Properties a2) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getPrintJob wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public void beep() {
        // ein Telefon piept nicht, weil Swing es moechte
    }

    @Override
    public java.awt.datatransfer.Clipboard getSystemClipboard() throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.getSystemClipboard wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    protected java.awt.EventQueue getSystemEventQueueImpl() {
        return warteschlange;
    }

    @Override
    public java.awt.dnd.peer.DragSourceContextPeer createDragSourceContextPeer(java.awt.dnd.DragGestureEvent a0) throws java.awt.dnd.InvalidDnDOperationException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.createDragSourceContextPeer wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public boolean isModalityTypeSupported(java.awt.Dialog.ModalityType a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.isModalityTypeSupported wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public boolean isModalExclusionTypeSupported(java.awt.Dialog.ModalExclusionType a0) {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.isModalExclusionTypeSupported wird nicht gebraucht — Swing ist leichtgewichtig");
    }

    @Override
    public java.util.Map<java.awt.font.TextAttribute, ?> mapInputMethodHighlight(java.awt.im.InputMethodHighlight a0) throws java.awt.HeadlessException {
        throw new UnsupportedOperationException("tsbMobile: Toolkit.mapInputMethodHighlight wird nicht gebraucht — Swing ist leichtgewichtig");
    }
}
