require 'yaml'

path = './.github/workflows/native-distribution.yml'
workflow = YAML.load_file(path)
triggers = workflow['on'] || workflow[true]
abort 'missing native workflow contract' unless triggers.fetch('workflow_call').fetch('inputs').fetch('source-ref').fetch('required')
job = workflow.fetch('jobs').fetch('build-native')
targets = job.fetch('strategy').fetch('matrix').fetch('settings').map { |item| item.fetch('target') }
gnu_path = './.github/workflows/windows-gnu-cabi-candidate.yml'
gnu_call = workflow.fetch('jobs').fetch('windows-gnu')
abort 'wrong GNU producer caller' unless gnu_call.fetch('uses') == gnu_path
abort 'GNU source is not forwarded' unless gnu_call.fetch('with').fetch('source-ref') == '${{ inputs.source-ref }}'
gnu = YAML.load_file(gnu_path)
gnu_triggers = gnu['on'] || gnu[true]
abort 'missing GNU workflow source contract' unless gnu_triggers.fetch('workflow_call').fetch('inputs').fetch('source-ref').fetch('required')
abort 'duplicate standalone GNU PR producer' if gnu_triggers.key?('pull_request')
gnu_jobs = gnu.fetch('jobs')
gnu_targets = gnu_jobs.fetch('cabi-gnullvm').fetch('strategy').fetch('matrix').fetch('include').map { |item| item.fetch('target') }
expected = %w[x86_64-unknown-linux-gnu aarch64-unknown-linux-gnu x86_64-apple-darwin aarch64-apple-darwin x86_64-pc-windows-gnullvm aarch64-pc-windows-gnullvm]
abort 'incomplete combined native target matrix' unless (targets + gnu_targets).sort == expected.sort
abort 'wrong GNU target matrix' unless gnu_targets.sort == %w[x86_64-pc-windows-gnullvm aarch64-pc-windows-gnullvm].sort
consumers = gnu_jobs.fetch('java-consumers')
abort 'Java consumers must follow actual GNU producers' unless consumers.fetch('needs') == 'cabi-gnullvm'
actual = consumers.fetch('strategy').fetch('matrix').fetch('include').map { |item| [item.fetch('os'), item.fetch('arch'), item.fetch('jdk'), item.fetch('target')] }
required = [
  ['windows-latest', 'x64', '17', 'x86_64-pc-windows-gnullvm'],
  ['windows-latest', 'x64', '21', 'x86_64-pc-windows-gnullvm'],
  ['windows-11-arm', 'arm64', '21', 'aarch64-pc-windows-gnullvm'],
]
abort 'incomplete genuine Java consumer matrix' unless actual.sort == required.sort
staging = gnu_jobs.fetch('stage-release')
abort 'missing complete GNU consumer gate' unless staging.fetch('needs').sort == %w[cabi-gnullvm java-consumers].sort
abort 'incomplete GNU release role matrix' unless staging.fetch('strategy').fetch('matrix').fetch('target').sort == gnu_targets.sort
stage_steps = staging.fetch('steps')
abort 'missing GNU release staging command' unless stage_steps.any? { |step| step['run'].to_s.include?('python -I scripts/stage_java_gnu_release.py') }
stage_uploads = stage_steps.select { |step| step['uses'].to_s.start_with?('actions/upload-artifact@') }
abort 'wrong GNU distribution artifact role' unless stage_uploads.length == 1 && stage_uploads[0].fetch('with').fetch('name') == 'native-distribution-${{ matrix.target }}' && !stage_uploads[0].key?('if')
stager = File.read('scripts/stage_java_gnu_release.py')
%w[ktav_cabi-windows-amd64.dll ktav_cabi-windows-arm64.dll .NOTICE.json .NOTICE.manifest.json check_embedded_notices.py source_ref target].each do |role|
  abort "missing GNU delivery role #{role}" unless stager.include?(role)
end
caller = YAML.load_file('.github/workflows/native-distribution-check.yml').fetch('jobs').fetch('native-distribution')
abort 'wrong native CI caller' unless caller.fetch('uses') == path
abort 'native CI source is not pinned' unless caller.fetch('with').fetch('source-ref').match?(/\A[0-9a-f]{40}\z/)
release = YAML.load_file('.github/workflows/release.yml').fetch('jobs')
producer = release.fetch('build-native')
abort 'production native build differs from CI' unless producer.fetch('uses') == path
abort 'production source must follow caller commit' unless producer.fetch('with').fetch('source-ref').include?('github.sha')
%w[publish-maven release].each do |name|
  abort "missing native gate for #{name}" unless release.fetch(name).fetch('needs').include?('build-native')
end
downloads = release.fetch('release').fetch('steps').select { |step| step['uses'].to_s.start_with?('actions/download-artifact@') }
selectors = downloads.map { |step| step.fetch('with').slice('name', 'pattern') }
abort 'unsafe release artifact download' unless selectors == [{'pattern' => 'native-distribution-*'}, {'name' => 'jars'}]
uploads = job.fetch('steps').select { |step| step['uses'].to_s.start_with?('actions/upload-artifact@') }
distribution = uploads.find { |step| step.fetch('with').fetch('name').start_with?('native-distribution-') }
abort 'missing native notice sidecars' unless distribution && distribution.fetch('with').fetch('path').include?('.NOTICE.json') && distribution.fetch('with').fetch('path').include?('.NOTICE.manifest.json')
puts 'six native distribution roles, genuine GNU consumers and publication gates verified'
