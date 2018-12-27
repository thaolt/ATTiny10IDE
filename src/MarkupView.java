import com.github.rjeschke.txtmark.Processor;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.prefs.Preferences;

class MarkupView extends JPanel {
  private JEditorPane         jEditorPane;
  private ArrayList<String>   stack = new ArrayList<>();
  private String              basePath = "", currentPage;

  class MyImageView extends ImageView {
    String loc;

    private MyImageView (Element elem) {
      super(elem);
      try {
        loc = basePath + URLDecoder.decode((String) elem.getAttributes().getAttribute(HTML.Attribute.SRC), "UTF-8");
      } catch (UnsupportedEncodingException ex) {
        ex.printStackTrace();
      }
    }

    @Override
    public URL getImageURL () {
      return getClass().getResource(loc);
    }
  }

  class MyViewFactory implements ViewFactory {
    ViewFactory view;

    private MyViewFactory (ViewFactory view) {
      this.view = view;
    }

    public View create (Element elem) {
      AttributeSet attrs = elem.getAttributes();
      Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
      Object obj = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
      if (obj instanceof HTML.Tag && obj == HTML.Tag.IMG) {
        return new MyImageView(elem);
      }
      return view.create(elem);
    }
  }

  class MyEditorKit extends HTMLEditorKit {
    @Override
    public ViewFactory getViewFactory () {
      return new MyViewFactory(super.getViewFactory());
    }
  }

  MarkupView (String markup) {
    int idx = markup.lastIndexOf("/");
    if (idx >= 0) {
      basePath = markup.substring(0, idx + 1);
      markup = markup.substring(idx + 1);
    }
    setLayout(new BorderLayout());
    jEditorPane = new JEditorPane();
    JScrollPane scrollPane = new JScrollPane(jEditorPane);
    JButton back = new JButton("BACK");
    jEditorPane.addHyperlinkListener(ev -> {
      if (ev.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        String link = ev.getURL().toString();
        if (link.startsWith("file://")) {
          link = link.substring(7);
          stack.add(currentPage);
          loadPage(basePath + link);
          back.setVisible(stack.size() > 0);
        } else {
          if (Desktop.isDesktopSupported()) {
            try {
              Desktop.getDesktop().browse(new URI(link));
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
        }
      }
    });
    jEditorPane.setEditable(false);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    add(scrollPane, BorderLayout.CENTER);
    back.addActionListener(e -> {
      if (stack.size() > 0) {
        String prev = stack.remove(stack.size() - 1);
        loadPage(prev);
        jEditorPane.setCaretPosition(0);
        back.setVisible(stack.size() > 0);
      }
    });
    add(back, BorderLayout.NORTH);
    back.setVisible(false);
    HTMLEditorKit kit = new MyEditorKit();
    jEditorPane.setEditorKit(kit);
    // Setup some basic markdown styles
    StyleSheet styleSheet = kit.getStyleSheet();
    styleSheet.addRule("body {color:#000; font-family:times; margin: 4px; }");
    styleSheet.addRule("h1 {font-size: 24px; font-weight: 600;}");
    styleSheet.addRule("h2 {font-size: 20px; font-weight: 600;}");
    styleSheet.addRule("h3 {font-size: 16px; font-weight: 600;}");
    styleSheet.addRule("h4 {font-size: 14px; font-weight: 600;}");
    styleSheet.addRule("h5 {font-size: 12px; font-weight: 600;}");
    styleSheet.addRule("h6 {font-size: 10px; font-weight: 600;}");
    styleSheet.addRule("pre {margin-left: 0.5cm}");
    styleSheet.addRule("ol {margin-left: 1cm;}");
    styleSheet.addRule("ol li {font-size: 14px; margin-top: 3px; margin-bottom: 3px;}");
    styleSheet.addRule("ul li {font-size: 14px; margin-top: 3px; margin-bottom: 3px;}");
    styleSheet.addRule("code {font-size: 12px; margin-bottom: 3px;}");
    styleSheet.addRule("p {font-size: 14px; margin-top: 5px; margin-bottom: 5px;}");
    loadPage(basePath + markup);
  }

  private void loadPage (String loc) {
    try {
      jEditorPane.setText(Processor.process(new String(getResource(loc))));
      currentPage = loc;
      jEditorPane.setCaretPosition(0);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private byte[] getResource (String file) throws IOException {
    InputStream fis = getClass().getResourceAsStream(file);
    if (fis != null) {
      byte[] data = new byte[fis.available()];
      fis.read(data);
      fis.close();
      return data;
    }
    throw new IllegalStateException("MarkupView.getResource() " + file + " not found");
  }

  public static void main (String[] args)  {
    Preferences prefs = Preferences.userRoot().node(MarkupView.class.getName());
    JFrame frame = new JFrame();
    MarkupView mView = new MarkupView("documentation/demopage1.md");
    frame.add(mView, BorderLayout.CENTER);
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.setSize(prefs.getInt("window.width", 800), prefs.getInt("window.height", 900));
    frame.setLocation(prefs.getInt("window.x", 10), prefs.getInt("window.y", 10));
    // Add window close handler
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing (WindowEvent ev) {
        System.exit(0);
      }
    });
    // Track window resize/move events and save in prefs
    frame.addComponentListener(new ComponentAdapter() {
      public void componentMoved (ComponentEvent ev)  {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.x", bounds.x);
        prefs.putInt("window.y", bounds.y);
      }
      public void componentResized (ComponentEvent ev)  {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.width", bounds.width);
        prefs.putInt("window.height", bounds.height);
      }
    });
    frame.setVisible(true);
  }
}
