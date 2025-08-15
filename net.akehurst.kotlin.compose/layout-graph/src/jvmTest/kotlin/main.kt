import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import net.akehurst.kotlin.components.layout.graph.GraphLayoutGraph
import net.akehurst.kotlin.components.layout.graph.GraphLayoutState
import net.akehurst.kotlin.components.layout.graph.GraphLayoutView
import kotlin.test.Test


class test_MultiPaneLayout {

    fun GraphLayoutGraph.addNode(id:String, color: Color,width:Int, height:Int) = this.addNode(id) {
        Column(
            modifier = Modifier
                .size(width.dp, height.dp)
                .background(color)
        ) {
            Text(id)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun main() {
        // initial layout
        val graph = GraphLayoutGraph("init")
        graph.addNode("A",Color.Red,50,50)
       // graph.addNode("B",Color.Blue,150, 150)
        graph.addNode("C",Color.Yellow,50, 50)

        graph.addEdge("e1", "A", "C")
       // graph.addEdge("e2", "B", "C")

        //println(graph.asString(""))
        val layoutState = GraphLayoutState(graph)

        singleWindowApplication(
            title = "Demo MultiPaneLayout",
        ) {
            GraphLayoutView(
                state = layoutState,
                modifier = Modifier
                    .background(Color.Green),
            )
        }
    }
}


