package mesosphere.marathon.api.v2

import javax.inject.Inject
import javax.ws.rs._
import javax.ws.rs.core.{ MediaType, Response }
import javax.ws.rs.core.Response.Status._

import mesosphere.marathon.api.RestResource
import mesosphere.marathon.api.v2.json.Formats._
import mesosphere.marathon.state.{ Group, GroupManager }
import mesosphere.marathon.upgrade.DeploymentManager.DeploymentStepInfo
import mesosphere.marathon.upgrade.{ DeploymentAction, DeploymentPlan }
import mesosphere.marathon.{ MarathonConf, MarathonSchedulerService }
import mesosphere.util.Logging
import play.api.libs.json.Json

@Path("v2/deployments")
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class DeploymentsResource @Inject() (
  service: MarathonSchedulerService,
  groupManager: GroupManager,
  val config: MarathonConf)
    extends RestResource
    with Logging {

  import mesosphere.util.ThreadPoolContext.context

  @GET
  def running(): Response = ok(result(service.listRunningDeployments()).map {
    case (plan, currentStep) => toInfo(plan, currentStep)
  })

  @POST
  @Path("/generate")
  def generate(body: Array[Byte]): Response = {
    val group = Json.parse(body).as[Group]

    val planFuture = groupManager.group(group.id).map { oldGroup =>
      Json.obj(
        "steps" -> DeploymentPlan(oldGroup.getOrElse(Group.empty), group).steps
      )
    }

    ok(result(planFuture).toString())
  }

  @DELETE
  @Path("{id}")
  def cancel(
    @PathParam("id") id: String,
    @DefaultValue("false")@QueryParam("force") force: Boolean): Response =
    result(service.listRunningDeployments())
      .find(_._1.id == id)
      .fold(notFound(s"DeploymentPlan $id does not exist")) {
        case (plan, _) if force =>
          // do not create a new deployment to return to the previous state
          log.info(s"Canceling deployment [$id]")
          service.cancelDeployment(id)
          status(ACCEPTED) // 202: Accepted
        case (plan, _) =>
          // create a new deployment to return to the previous state
          deploymentResult(result(groupManager.update(
            plan.original.id,
            _ => plan.original,
            force = true
          )))
      }
  private def toInfo(
    deployment: DeploymentPlan,
    currentStepInfo: DeploymentStepInfo): Map[String, Any] =
    Map(
      "id" -> deployment.id,
      "version" -> deployment.version,
      "affectedApps" -> deployment.affectedApplicationIds.map(_.toString),
      "steps" -> deployment.steps.map(step => step.actions.map(actionToMap)),
      "currentActions" -> currentStepInfo.step.actions.map(actionToMap),
      "currentStep" -> currentStepInfo.nr,
      "totalSteps" -> deployment.steps.size
    )

  def actionToMap(action: DeploymentAction): Map[String, String] =
    Map(
      "action" -> action.getClass.getSimpleName,
      "app" -> action.app.id.toString
    )
}
