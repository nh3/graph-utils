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
          "Usage: RenderGraph (layout|paint|both|none) [options] <graph>\n\n"
        + "Common Options:\n"
        + "  -o <str>               output graph [default: none]\n"
        + "  -p <str>               output pdf [default: none]\n"
        + "  --scale-size           scale node size by degree\n"
        + "  --show-label           show node label\n"
        + "layout options:\n"
        + "  --seed <int>           random seed for OpenOrd layout [default: 0]\n"
        + "  --time <int>           running iterations for Force-Atlas layout [default: 20]\n"
        + "  --novlp-time <int>     running iterations for no-overlap adjustment (must not exceed --time) [default: 5]\n"
        + "paint options:\n"
        + "  -c <str>               column name that defines color-coding [default: color]\n"
        + "  --color <str>          comma separated list of colors\n\n";

    public static void main(String[] args) {
        Map<String, Object> opts = new Docopt(doc).parse(args);
        //for (String key: opts.keySet())
        //    System.out.println(key + "=" + opts.get(key));
        String inputGraph = (String)opts.get("<graph>");
        boolean runLayout = (boolean)opts.get("layout");
        boolean runPaint = (boolean)opts.get("paint");
        boolean runBoth = (boolean)opts.get("both");
        boolean runNone = (boolean)opts.get("none");
        String outputGraph = (String)opts.get("-o");
        String outputPdf = (String)opts.get("-p");
        boolean scaleSize = (boolean)opts.get("--scale-size");
        boolean showLabel = (boolean)opts.get("--show-label");
        long runLayoutSeed = Long.parseLong((String)opts.get("--seed"));
        int runLayoutTime = Integer.parseInt((String)opts.get("--time"));
        int noOverlapTime = Integer.parseInt((String)opts.get("--novlp-time"));
        String colorColumn = (String)opts.get("-c");

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
            OpenOrdLayout firstLayout = new OpenOrdLayout(null);
            firstLayout.resetPropertiesValues();
            firstLayout.setRandSeed(runLayoutSeed);
            firstLayout.setEdgeCut(0.1f);
            firstLayout.setNumIterations(750);
            firstLayout.setLiquidStage(25);
            firstLayout.setExpansionStage(25);
            firstLayout.setCooldownStage(25);
            firstLayout.setCrunchStage(10);
            firstLayout.setSimmerStage(15);

            ForceAtlasLayout secondLayout = new ForceAtlasLayout(null);
            secondLayout.resetPropertiesValues();
            secondLayout.setAdjustSizes(false);

            firstLayout.setGraphModel(graphModel);
            firstLayout.initAlgo();
            while (firstLayout.canAlgo()) {
                firstLayout.goAlgo();
            }
            firstLayout.endAlgo();

            secondLayout.setGraphModel(graphModel);
            secondLayout.initAlgo();
            for (int i=0; i<runLayoutTime && secondLayout.canAlgo(); i++) {
                if (i>=runLayoutTime-noOverlapTime) { secondLayout.setAdjustSizes(true); }
                secondLayout.goAlgo();
            }
            secondLayout.endAlgo();
        }

        if (scaleSize) {
            Function degreeRanking = appearanceModel.getNodeFunction(graph, AppearanceModel.GraphFunction.NODE_DEGREE, RankingNodeSizeTransformer.class);
            RankingNodeSizeTransformer degreeTransformer = (RankingNodeSizeTransformer) degreeRanking.getTransformer();
            degreeTransformer.setMinSize(10);
            degreeTransformer.setMaxSize(50);
            appearanceController.transform(degreeRanking);
        }

        if (runPaint || runBoth) {
            Column column = graphModel.getNodeTable().getColumn(colorColumn);
            Function attributePartition = appearanceModel.getNodeFunction(graph, column, PartitionElementColorTransformer.class);
            Partition partition = ((PartitionFunction) attributePartition).getPartition();
            int nValues = partition.size();
            System.err.println(partition.getValues());
            Palette palette;
            try {
                String[] names = ((String)opts.get("--color")).split(",");
                Color[] colors = new Color[nValues];
                for (int i=0; i<nValues; i++) {
                    colors[i] = (Color)Color.class.getField(names[i]).get(null);
                }
                palette = new Palette(colors);
            } catch (Exception ex) {
                System.err.println(ex);
                System.err.println("Error using specified colors, revert to random palette");
                palette = PaletteManager.getInstance().generatePalette(nValues);
            }
            partition.setColors(palette.getColors());
            appearanceController.transform(attributePartition);
        }

        if (!runNone) {
            previewModel.getProperties().putValue(PreviewProperty.NODE_OPACITY, 90);
            previewModel.getProperties().putValue(PreviewProperty.NODE_BORDER_WIDTH, 0f);
            if (showLabel) {
                previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
                previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.TRUE);
                previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("SANS_SERIF", 0, 2));
            }
            previewModel.getProperties().putValue(PreviewProperty.EDGE_CURVED, Boolean.FALSE);
            previewModel.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Color.GRAY));
            previewModel.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 90);
        }

        try {
            if (!outputGraph.equals("none")) { exportController.exportFile(new File(outputGraph)); }
            if (!runNone && !outputPdf.equals("none")) { exportController.exportFile(new File(outputPdf)); }
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }
}
