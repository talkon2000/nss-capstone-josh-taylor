package com.nashss.se.chessplayerservice.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.nashss.se.chessplayerservice.activity.request.GetNextMoveRequest;
import com.nashss.se.chessplayerservice.activity.response.GetNextMoveResponse;

public class GetNextMoveLambda extends LambdaActivityRunner<GetNextMoveRequest, GetNextMoveResponse>
        implements RequestHandler<com.nashss.se.chessplayerservice.lambda.LambdaRequest<GetNextMoveRequest>, com.nashss.se.chessplayerservice.lambda.LambdaResponse> {


    @Override
    public LambdaResponse handleRequest(LambdaRequest<GetNextMoveRequest> input, Context context) {
        return super.runActivity(
                () -> input.fromPathAndQuery((path, query) -> GetNextMoveRequest.builder()
                        .withMove(path.get("move"))
                        .withGameId(query.get("gameId"))
                        .build()),
                (request, serviceComponent) -> serviceComponent.provideGetNextMoveActivity().handleRequest(request)
        );
    }
}