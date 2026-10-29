require 'yaml'

path = './.github/workflows/native-distribution.yml'
workflow = YAML.load_file(path)
triggers = workflow['on'] || workflow[true]
abort 'missing native workflow contract' unless triggers.fetch('workflow_call').fetch('inputs').fetch('source-ref').fetch('required')
job = workflow.fetch('jobs').fetch('build-native')
targets = job.fetch('strategy').fetch('matrix').fetch('settings').map { |item| item.fetch('target') }
expected = %w[x86_64-unknown-linux-gnu aarch64-unknown-linux-gnu x86_64-apple-darwin aarch64-apple-darwin x86_64-pc-windows-msvc aarch64-pc-windows-msvc]
abort 'incomplete native target matrix' unless targets.sort == expected.sort
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
puts 'native distribution workflow and publication gates verified'
