import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import org.docopt.Docopt;
import org.gephi.appearance.api.*;
import org.gephi.appearance.plugin.*;
import org.gephi.appearance.plugin.palette.*;
import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.*;
import org.gephi.io.importer.api.*;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.AutoLayout;
import org.gephi.layout.plugin.forceAtlas.ForceAtlasLayout;
import org.gephi.layout.plugin.openord.OpenOrdLayout;
import org.gephi.preview.api.*;
import org.gephi.preview.types.*;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

public class RenderGraph {
    private static final String doc =
          "Usage: RenderGraph (layout|paint|both) [options] <graph>\n\n"
        + "Common Options:\n"
        + "  -o <str>       output prefix [default: out]\n"
        + "layout options:\n"
        + "  -t <int>       layout running time in seconds [default: 30]\n"
        + "paint options:\n"
        + "  -c <str>       column name that defines color-coding [default: color]\n"
        + "  --color <str>  comma separated list of colors\n\n";

    public static void main(String[] args) {
        Map<String, Object> opts = new Docopt(doc).parse(args);
        //for (String key: opts.keySet())
        //    System.out.println(key + "=" + opts.get(key));
        String inputGraph = (String)opts.get("<graph>");
        boolean runLayout = (boolean)opts.get("layout");
        boolean runPaint = (boolean)opts.get("paint");
        boolean runBoth = (boolean)opts.get("both");
        String outputPrefix = (String)opts.get("-o");
        int runLayoutSeconds = Integer.parseInt((String)opts.get("-t"));
        String colorColumn = (String)opts.get("-c");
        String outputGraph = (String)outputPrefix + ".gexf";
        String outputPdf = (String)outputPrefix + ".pdf";

        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        PreviewModel previewModel = Lookup.getDefault().lookup(PreviewController.class).getModel();
        ExportController exportController = Lookup.getDefault().lookup(ExportController.class);

        Container container;
        try {
            File file = new File(inputGraph);
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDirectionDefault.UNDIRECTED);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        importController.process(container, new DefaultProcessor(), workspace);
        UndirectedGraph graph = graphModel.getUndirectedGraph();

        if (runLayout || runBoth) {
            AutoLayout autoLayout = new AutoLayout(runLayoutSeconds, TimeUnit.SECONDS);
            autoLayout.setGraphModel(graphModel);
            OpenOrdLayout firstLayout = new OpenOrdLayout(null);
            firstLayout.setEdgeCut(0.0f);
            ForceAtlasLayout secondLayout = new ForceAtlasLayout(null);
            AutoLayout.DynamicProperty adjustSizesProperty = AutoLayout.createDynamicProperty("forceAtlas.adjustSizes.name", Boolean.TRUE, 0.7f);
            autoLayout.addLayout(firstLayout, 0.25f);
            autoLayout.addLayout(secondLayout, 0.75f, new AutoLayout.DynamicProperty[]{adjustSizesProperty});
            autoLayout.execute();
        }

        Function degreeRanking = appearanceModel.getNodeFunction(graph, AppearanceModel.GraphFunction.NODE_DEGREE, RankingNodeSizeTransformer.class);
        RankingNodeSizeTransformer degreeTransformer = (RankingNodeSizeTransformer) degreeRanking.getTransformer();
        degreeTransformer.setMinSize(10);
        degreeTransformer.setMaxSize(50);
        appearanceController.transform(degreeRanking);

        if (runPaint || runBoth) {
            Column column = graphModel.getNodeTable().getColumn(colorColumn);
            Function attributePartition = appearanceModel.getNodeFunction(graph, column, PartitionElementColorTransformer.class);
            Partition partition = ((PartitionFunction) attributePartition).getPartition();
            Palette palette;
            try {
                String[] names = ((String)opts.get("--color")).split(",");
                Color[] colors = new Color[names.length];
                for (int i=0; i<names.length; i++) {
                    colors[i] = (Color)Color.class.getField(names[i]).get(null);
                }
                palette = new Palette(colors);
            } catch (Exception ex) {
                System.err.println("Error using specified colors, revert to random palette");
                palette = PaletteManager.getInstance().generatePalette(partition.size());
            }
            partition.setColors(palette.getColors());
            appearanceController.transform(attributePartition);
        }

        previewModel.getProperties().putValue(PreviewProperty.NODE_OPACITY, 90);
        previewModel.getProperties().putValue(PreviewProperty.NODE_BORDER_WIDTH, new Float(0.1f));
        previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.TRUE);
        previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("SANS_SERIF", 0, 4));
        previewModel.getProperties().putValue(PreviewProperty.EDGE_CURVED, Boolean.FALSE);
        previewModel.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.GRAY));
        previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 90);

        try {
            exportController.exportFile(new File(outputGraph));
            exportController.exportFile(new File(outputPdf));
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

}
