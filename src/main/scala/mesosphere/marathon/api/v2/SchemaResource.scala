package mesosphere.marathon.api.v2

import javax.inject.{ Inject }
import javax.ws.rs._
import javax.ws.rs.core.{ MediaType }
import mesosphere.marathon.MarathonConf
import com.codahale.metrics.annotation.Timed
import mesosphere.marathon.api.{ ModelValidation, RestResource }
import java.io.InputStream

@Path("v2/schemas")
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class SchemaResource @Inject() (
    val config: MarathonConf) extends RestResource with ModelValidation {

  @GET
  @Timed
  def index(): InputStream = {
    getClass().getResourceAsStream("AppDefinition.json")
  }
}
