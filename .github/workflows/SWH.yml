on:
  push:
    tags:
      - '*'

jobs:
  swh-save:
    runs-on: ubuntu-latest
    name: Saves this repo to SWH
    steps:
    - name: Save step
      id: save
      uses: sdruskat/swh-save-action@v1.0.1
    # Use the output from the `save` step
    - name: Print result
      run: echo "The result from the API call was ${{ steps.save.outputs.result }}"
