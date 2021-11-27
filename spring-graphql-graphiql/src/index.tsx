import React from 'react';
import {render} from 'react-dom';
import GraphiQL from 'graphiql';
import {createGraphiQLFetcher} from '@graphiql/toolkit';
import 'graphiql/graphiql.css';
import 'regenerator-runtime/runtime';
import {LOGO, PATH, PROTOCOL, HOST} from './config';

const url = `${PROTOCOL}//${HOST}${PATH}`;
const subscriptionProtocol = PROTOCOL === 'https:' ? 'wss:' : 'ws:';
const subscriptionUrl = `${subscriptionProtocol}//${HOST}${PATH}`;
const fetcher = createGraphiQLFetcher({
  url,
  subscriptionUrl
});

export const App = () => (
  <GraphiQL fetcher={fetcher}>
    <GraphiQL.Logo>
      <span>{LOGO}</span>
    </GraphiQL.Logo>
  </GraphiQL>
);

render(<App />, document.getElementById('root'));
